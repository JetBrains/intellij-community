/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package de.fernflower.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DataInputFullStream extends DataInputStream {

    public DataInputFullStream(InputStream in) {
    	super(in);
    }
    
    public final int readFull(byte b[]) throws IOException {
    	
    	int length = b.length;
    	byte[] btemp = new byte[length];
    	int pos = 0;
    	
    	int bytes_read = -1;
    	for(;;) {
    		bytes_read = read(btemp, 0, length-pos);
    		if(bytes_read==-1) {
    			return -1;
    		}
    		
    		System.arraycopy(btemp, 0, b, pos, bytes_read);
    		pos+=bytes_read;
    		if(pos == length) {
    			break;
    		}
    	}

    	return length;
    }    
	
}
