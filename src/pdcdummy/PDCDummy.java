/**
 * 
 */
package pdcdummy;

import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.ISO7816;
import javacard.framework.APDU;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacardx.apdu.ExtendedLength;

/**
 * @author e-health
 *
 */
public class PDCDummy extends Applet implements ExtendedLength, ISO7816 {
	
	private byte[] pinBuf = new byte[6];
	
	final static byte INS_CARD_CHECKING1 = (byte) 0xB1;
	final static byte INS_CARD_CHECKING2 = (byte) 0xB2;
	
	final static byte INS_SET_CERT = (byte) 0xC2;
	final static byte INS_SET_BIODATA = (byte) 0xC3;
	final static byte INS_SET_MEDREC_STATIK = (byte) 0xC4;
	final static byte INS_INIT_TULIS_MEDREC_DINAMIK = (byte) 0xC5;
	final static byte INS_TULIS_MEDREC_DINAMIK = (byte) 0xC6;
	final static byte INS_FINAL_TULIS_MEDREC_DINAMIK = (byte) 0xC7;
	final static byte INS_HALF_PERSONALIZED = (byte) 0xC8;
	final static byte INS_STATE_PERSONALIZED = (byte) 0xC9;
	
	final static byte INS_READ_CERT = (byte) 0xD2;
	final static byte INS_READ_BIODATA = (byte) 0xD3;
	final static byte INS_READ_MEDREC_STATIK = (byte) 0xD4;
	final static byte INS_READ_MEDREC_DINAMIK = (byte) 0xD5;
	final static byte INS_READ_MEDREC_DINAMIK_TIMESTAMP = (byte) 0xD6;
	final static byte INS_READ_INDEX = (byte) 0xD7;
	
	final static byte PIN_TRY_LIMIT = 0x03;
	final static byte PIN_SIZE = 0x05;
	
	static final short CERT_LENGTH = (short)(0x11);
	static final short STATIK_LENGTH = (short)(0x44F);
	static final short BIODATA_LENGTH = (short)(0x399);
	static final short BIODATADL_LENGTH = (short)(0x2A);
	static final short RECORD_LENGTH = (short)(0x91E); // 2334
	
	static final byte TRANSACTION_INIT 	= 0;
	static final byte TRANSACTION_BEGIN = 1;
	static final byte TRANSACTION_END 	= 2;
	
	static final byte RECORD_OPEN = 0;
	static final byte RECORD_CLOSED = 1;
	
	static final short LENGTH_PUSKESMAS_ID = (short)0xC;
	
	byte recordPosition = (byte) 0;
	short mrdCapacity = (short)0x5;
	
	// patient data, tidak dengan history
	byte[] biodata = new byte[BIODATA_LENGTH];
	byte[] medrecStatik = new byte[STATIK_LENGTH];
	Object[] medrecDinamik = new Object[mrdCapacity]; // mrdCapacity
	byte[] recordFlag = new byte[mrdCapacity];
	
	static byte lifeState; //persistent memory
	/* nilai untuk lifeState */
	final static byte LIFE_STATE_VIRGIN 			= 0x00;
	final static byte LIFE_HALF_PERSONALIZED 		= 0x01;
	final static byte LIFE_STATE_PERSONALIZED 		= 0x11;
	
	// patient credentials
	byte[] cert = new byte[17]; // holder auth(1), NIK(16)
	
	static byte[] lastPuskesmas = new byte[LENGTH_PUSKESMAS_ID];
	static boolean[] isVerified;
	
	byte transactionFlag = TRANSACTION_END;
	
	OwnerPIN pin;
	
	private PDCDummy(byte[] bArray, short bOffset, byte bLength) {
		register(bArray, (short) (bOffset + 1), bArray[bOffset]);
		pin = new OwnerPIN(PIN_TRY_LIMIT, PIN_SIZE);
		short appletDataOff = (short) (1 + bArray[0]);
		appletDataOff += (short) (1 + bArray[appletDataOff]);
		pin.update(bArray, (short)(appletDataOff+1), PIN_SIZE);
		
		// transient memory
		isVerified = JCSystem.makeTransientBooleanArray((short)1, JCSystem.CLEAR_ON_DESELECT);
		lifeState = LIFE_STATE_VIRGIN;
		
		// medrec dinamik declaration
		medrecDinamik[0] = new byte[RECORD_LENGTH];
		medrecDinamik[1] = new byte[RECORD_LENGTH];
		medrecDinamik[2] = new byte[RECORD_LENGTH];
		medrecDinamik[3] = new byte[RECORD_LENGTH];
		medrecDinamik[4] = new byte[RECORD_LENGTH];
	}
	
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// GP-compliant JavaCard applet registration
		new PDCDummy(bArray, bOffset, bLength);
	}

	public void process(APDU apdu) {
		// Good practice: Return 9000 on SELECT
		if (selectingApplet()) {
			return;
		}
		
		short recvLen = apdu.setIncomingAndReceive();
		short lc = apdu.getIncomingLength();

		byte[] buf = apdu.getBuffer();
		switch (buf[ISO7816.OFFSET_INS]) {
		case INS_CARD_CHECKING1:
			cardChecking1(apdu);
			break;
		case INS_CARD_CHECKING2:
			cardChecking2(apdu);
			break;
		case INS_SET_CERT:
			setCert(apdu, buf, recvLen);
			break;
		case INS_SET_BIODATA:
			setBiodataChunck(apdu, buf, recvLen);
			break;
		case INS_HALF_PERSONALIZED:
			setBiodataChunck(apdu, buf, recvLen);
			break;
		case INS_SET_MEDREC_STATIK:
			setMedrecStatikChunck(apdu, buf, recvLen);
			break;
		case INS_STATE_PERSONALIZED:
			setMedrecStatikChunck(apdu, buf, recvLen);
			break;
		case INS_INIT_TULIS_MEDREC_DINAMIK:
			initTulisMedrecDinamik(apdu, buf);
			break;
		case INS_TULIS_MEDREC_DINAMIK:
			tulisMedrecDinamikChunk(apdu, buf, recvLen);
			break;
		case INS_FINAL_TULIS_MEDREC_DINAMIK:
			tulisMedrecDinamikChunk(apdu, buf, recvLen);
			break;
		case INS_READ_CERT:
			readCert(apdu);
			break;
		case INS_READ_BIODATA:
			readBiodata(apdu);
			break;
		case INS_READ_MEDREC_STATIK:
			readMedrecStatik(apdu);
			break;
		case INS_READ_MEDREC_DINAMIK:
			readMedrecDinamik(apdu, buf);
			break;
		case INS_READ_MEDREC_DINAMIK_TIMESTAMP:
			readMedrecDinamikTimestamp(apdu);
			break;
		case INS_READ_INDEX:
			readIndex(apdu);
			break;
		default:
			// good practice: If you don't know the INStruction, say so:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}
	
	void cardChecking1(APDU apdu){
		byte buffer[] = apdu.getBuffer();
		buffer[0] = lifeState;
		apdu.setOutgoingAndSend((short)0, (short)1);
	}
	
	void cardChecking2(APDU apdu){
		byte buffer[] = apdu.getBuffer();
		short offset = (short)0;
		buffer[offset] = transactionFlag;
		offset+=1;
		Util.arrayCopyNonAtomic(lastPuskesmas, (short)0, buffer, offset, LENGTH_PUSKESMAS_ID);
		offset+=LENGTH_PUSKESMAS_ID;
	
		apdu.setOutgoingAndSend((short)0, offset);
	}
	
	void readIndex(APDU apdu){
		byte buffer[] = apdu.getBuffer();
		buffer[0] = recordPosition; 
		apdu.setOutgoingAndSend((short)0, (short)1);
	}
	
	void setCert(APDU apdu, byte[] buf, short recvLen) {
		short pointer = 0;
		short offData = apdu.getOffsetCdata();
		while (recvLen > (short) 0) {
			Util.arrayCopyNonAtomic(buf, offData, cert, pointer, recvLen);
			pointer += recvLen;
			recvLen = apdu.receiveBytes(offData);
		}
	}
	
	void setBiodataChunck(APDU apdu, byte[] buf, short recvLen){
		if(lifeState == LIFE_STATE_VIRGIN){
			if(buf[OFFSET_INS] == INS_HALF_PERSONALIZED){
				lifeState = LIFE_HALF_PERSONALIZED;
			}
			
			short offData = apdu.getOffsetCdata();
			short startPointer = Util.getShort(buf, offData);
			short dataLength = Util.getShort(buf, (short) (offData+2));
			Util.arrayCopyNonAtomic(buf, (short) (offData+2+2), biodata, startPointer, dataLength);
		}
	}
	
	void setMedrecStatikChunck(APDU apdu, byte[] buf, short recvLen){
		if(lifeState == LIFE_HALF_PERSONALIZED){
			if(buf[OFFSET_INS] == INS_STATE_PERSONALIZED){
				lifeState = LIFE_STATE_PERSONALIZED;
			}
			
			short offData = apdu.getOffsetCdata();
			short startPointer = Util.getShort(buf, offData);
			short dataLength = Util.getShort(buf, (short) (offData+2));
			Util.arrayCopyNonAtomic(buf, (short) (offData+2+2), medrecStatik, startPointer, dataLength);
		}
	}
	
	void initTulisMedrecDinamik(APDU apdu, byte[] buf) {
		short offData = apdu.getOffsetCdata();
		transactionFlag = TRANSACTION_INIT;
		// set lastpuskesmas ID
		Util.arrayCopyNonAtomic(buf, offData, lastPuskesmas, (short)0, LENGTH_PUSKESMAS_ID);
		transactionFlag = TRANSACTION_BEGIN;
	}
	
	void tulisMedrecDinamikChunk(APDU apdu, byte[] buf, short recvLen) {
		
		transactionFlag = TRANSACTION_BEGIN;
		short idx = Util.getShort(buf, OFFSET_P1);
		boolean isLastWrittenRecord = false;
		if (buf[OFFSET_INS] == INS_FINAL_TULIS_MEDREC_DINAMIK) {
			isLastWrittenRecord = true;
		}
		
		recordFlag[idx] = RECORD_OPEN;
		short offData = apdu.getOffsetCdata();
		short startPointer = Util.getShort(buf, offData);
		short dataLength = Util.getShort(buf, (short) (offData+2));
		Util.arrayCopyNonAtomic(buf, (short) (offData+2+2), (byte[])medrecDinamik[idx], startPointer, dataLength);
		recordFlag[idx] = RECORD_CLOSED;
		
		if (isLastWrittenRecord) {
			transactionFlag = TRANSACTION_END;
			if(recordPosition == (byte)5){
				recordPosition = (byte)5;
			} else {
				recordPosition +=1;
			}
		}
	}
	
	void readCert(APDU apdu) {
		apdu.setOutgoing();
		apdu.setOutgoingLength(CERT_LENGTH);
		apdu.sendBytesLong(cert, (short)0, CERT_LENGTH);
	}
	
	void readBiodata(APDU apdu) {
		apdu.setOutgoing();
		apdu.setOutgoingLength(BIODATA_LENGTH);
		apdu.sendBytesLong(biodata, (short)0, BIODATA_LENGTH);
	}
	
	void readMedrecStatik(APDU apdu) {
		apdu.setOutgoing();
		apdu.setOutgoingLength(STATIK_LENGTH);
		apdu.sendBytesLong(medrecStatik, (short)0, STATIK_LENGTH);
	}
	
	void readMedrecDinamik(APDU apdu, byte[] buf) {
		short idx = Util.getShort(buf, OFFSET_P1);
		byte[] record = (byte[]) medrecDinamik[idx];
		apdu.setOutgoing();
		apdu.setOutgoingLength(RECORD_LENGTH);
		apdu.sendBytesLong(record, (short)0, RECORD_LENGTH);
	}
	
	void readMedrecDinamikTimestamp(APDU apdu){
		byte buffer[] = apdu.getBuffer();
		short offset = 0;
		for(short i=0;i<recordPosition;i++){
//			if (recordFlag[i] == RECORD_CLOSED){
				Util.arrayCopyNonAtomic((byte[])medrecDinamik[i], (short)4, buffer, offset, (short)4);
				offset+=4;
//			}
		}
		apdu.setOutgoing();
		apdu.setOutgoingLength(offset);
		apdu.sendBytesLong(buffer, (short)0, offset);
	}
}