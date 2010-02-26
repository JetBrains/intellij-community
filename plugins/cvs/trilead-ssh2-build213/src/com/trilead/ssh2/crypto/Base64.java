
package com.trilead.ssh2.crypto;

import java.io.CharArrayWriter;
import java.io.IOException;

/**
 * Basic Base64 Support.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: Base64.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public class Base64
{
	static final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
	
	public static char[] encode(byte[] content)
	{
		CharArrayWriter cw = new CharArrayWriter((4 * content.length) / 3);

		int idx = 0;
					
		int x = 0;

		for (int i = 0; i < content.length; i++)
		{
			if (idx == 0)
				x = (content[i] & 0xff) << 16;
			else if (idx == 1)
				x = x | ((content[i] & 0xff) << 8);
			else
				x = x | (content[i] & 0xff);

			idx++;

			if (idx == 3)
			{
				cw.write(alphabet[x >> 18]);
				cw.write(alphabet[(x >> 12) & 0x3f]);
				cw.write(alphabet[(x >> 6) & 0x3f]);
				cw.write(alphabet[x & 0x3f]);

				idx = 0;
			}
		}

		if (idx == 1)
		{
			cw.write(alphabet[x >> 18]);
			cw.write(alphabet[(x >> 12) & 0x3f]);
			cw.write('=');
			cw.write('=');
		}

		if (idx == 2)
		{
			cw.write(alphabet[x >> 18]);
			cw.write(alphabet[(x >> 12) & 0x3f]);
			cw.write(alphabet[(x >> 6) & 0x3f]);
			cw.write('=');
		}

		return cw.toCharArray();
	}

	public static byte[] decode(char[] message) throws IOException
	{
		byte buff[] = new byte[4];
		byte dest[] = new byte[message.length];

		int bpos = 0;
		int destpos = 0;

		for (int i = 0; i < message.length; i++)
		{
			int c = message[i];

			if ((c == '\n') || (c == '\r') || (c == ' ') || (c == '\t'))
				continue;

			if ((c >= 'A') && (c <= 'Z'))
			{
				buff[bpos++] = (byte) (c - 'A');
			}
			else if ((c >= 'a') && (c <= 'z'))
			{
				buff[bpos++] = (byte) ((c - 'a') + 26);
			}
			else if ((c >= '0') && (c <= '9'))
			{
				buff[bpos++] = (byte) ((c - '0') + 52);
			}
			else if (c == '+')
			{
				buff[bpos++] = 62;
			}
			else if (c == '/')
			{
				buff[bpos++] = 63;
			}
			else if (c == '=')
			{
				buff[bpos++] = 64;
			}
			else
			{
				throw new IOException("Illegal char in base64 code.");
			}

			if (bpos == 4)
			{
				bpos = 0;

				if (buff[0] == 64)
					break;

				if (buff[1] == 64)
					throw new IOException("Unexpected '=' in base64 code.");

				if (buff[2] == 64)
				{
					int v = (((buff[0] & 0x3f) << 6) | ((buff[1] & 0x3f)));
					dest[destpos++] = (byte) (v >> 4);
					break;
				}
				else if (buff[3] == 64)
				{
					int v = (((buff[0] & 0x3f) << 12) | ((buff[1] & 0x3f) << 6) | ((buff[2] & 0x3f)));
					dest[destpos++] = (byte) (v >> 10);
					dest[destpos++] = (byte) (v >> 2);
					break;
				}
				else
				{
					int v = (((buff[0] & 0x3f) << 18) | ((buff[1] & 0x3f) << 12) | ((buff[2] & 0x3f) << 6) | ((buff[3] & 0x3f)));
					dest[destpos++] = (byte) (v >> 16);
					dest[destpos++] = (byte) (v >> 8);
					dest[destpos++] = (byte) (v);
				}
			}
		}

		byte[] res = new byte[destpos];
		System.arraycopy(dest, 0, res, 0, destpos);

		return res;
	}
}
