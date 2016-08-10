package com.intellij.openapi.diff.impl.patch.lib.base85xjava;

import com.intellij.util.containers.hash.HashMap;

/**
 * The main Base85x-java program
 * 
 * @author Simon Warta, Kullo
 * @version 0.1
 */
public class Base85x {

    private static final char[] ALPHABET_85 = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
            'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
            'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
            'u', 'v', 'w', 'x', 'y', 'z',
            '!', '#', '$', '%', '&', '(', ')', '*', '+', '-',
            ';', '<', '=', '>', '?', '@', '^', '_', '`', '{',
            '|', '}', '~'
    };
    
    private static HashMap<Character, Integer> INDEX_OF_MAP = initIndexMap();

        private static HashMap<Character, Integer> initIndexMap() {
          HashMap<Character, Integer> result = new HashMap<>(ALPHABET_85.length);
          for (int i = 0; i < ALPHABET_85.length; i++) {
            result.put(ALPHABET_85[i], i);
          }
          return result;
        }

        public static char getCharAt(int i) {
		return ALPHABET_85[i];
        }

        public static int getIndexOf(char c) {
		return INDEX_OF_MAP.get(c);
        }
        
	public static char[] encode(String data) {
		return Base85x.encode(data.getBytes());
	}
	
	public static byte[] decode(String data) {
		return Base85x.decode(data.toCharArray());
	}
	
	public static char[] encode(byte[] data) {
		int length = data.length;
		char[] out = new char[(length/4)*5 + ((length%4 != 0) ? length%4+1 : 0)];
		int k = 0;
		// 64 bit integer
		long b;
		int c1, c2, c3, c4, c5;
		int rest;
		int i;
		
		for (i = 0; i+4 <= length; i += 4) {
			b = 0L;
			b |= (int) data[i]   & 0xFF;
			b <<= 8;
			b |= (int) data[i+1] & 0xFF;
			b <<= 8;
			b |= (int) data[i+2] & 0xFF;
			b <<= 8;
			b |= (int) data[i+3] & 0xFF;

			c5 = (int) (b%85);
			b /= 85;
			c4 = (int) (b%85);
			b /= 85;
			c3 = (int) (b%85);
			b /= 85;
			c2 = (int) (b%85);
			b /= 85;
			c1 = (int) (b%85);

			out[k] = getCharAt(c1); k++;
			out[k] = getCharAt(c2); k++;
			out[k] = getCharAt(c3); k++;
			out[k] = getCharAt(c4); k++;
			out[k] = getCharAt(c5); k++;
		}
		if ((rest = length%4) != 0) {
			int j;
			byte[] block = {'~', '~', '~', '~'};
			for (j = 0; j < rest; j++) {
				block[j] = data[i+j]; 
			}
			char[] out_rest = Base85x.encode(block); 
			for (j = 0; j < rest+1; j++) {
				out[k] = out_rest[j];
				k++;
			}
		}
		return out;
	}
	
	public static byte[] decode(char[] data) {
		int length = data.length;
		byte[] out = new byte[(length/5)*4 + ((length%5 != 0) ? length%5-1 : 0)];
		int k = 0;
		int rest;
		int i;
		int b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0;
		int b = 0;
		
		for (i = 0; i+5 <= length; i += 5) {
			b1 = getIndexOf(data[i]);
			b2 = getIndexOf(data[i + 1]);
			b3 = getIndexOf(data[i + 2]);
			b4 = getIndexOf(data[i + 3]);
			b5 = getIndexOf(data[i + 4]);

			// overflow into negative numbers
			// is normal and does not do any damage because
			// of the cut operations below
			b = b1*52200625 + b2*614125 + b3*7225 + b4*85 + b5;
		    
			out[k] = (byte) ((b >>> 24) & 0xFF); k++;
			out[k] = (byte) ((b >>> 16) & 0xFF); k++;
			out[k] = (byte) ((b >>>  8) & 0xFF); k++;
			out[k] = (byte) (b & 0xFF);          k++;
		}
				
		if ((rest = length%5) != 0) {
			int j;
			char[] block = {'~', '~', '~', '~', '~'};
			for (j = 0; j < rest; j++) {
				block[j] = data[i+j];
			}
			byte[] out_rest = Base85x.decode(block);
			for (j = 0; j < rest-1; j++) {
				out[k] = out_rest[j];
				k++;
			}
		}

		return out;
	}	
}
