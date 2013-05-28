
package com.trilead.ssh2_build213.packets;

import com.trilead.ssh2_build213.util.Tokenizer;

import java.io.IOException;
import java.math.BigInteger;


/**
 * TypesReader.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: TypesReader.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */
public class TypesReader
{
	byte[] arr;
	int pos = 0;
	int max = 0;

	public TypesReader(byte[] arr)
	{
		this.arr = arr;
		pos = 0;
		max = arr.length;
	}

	public TypesReader(byte[] arr, int off)
	{
		this.arr = arr;
		this.pos = off;
		this.max = arr.length;

		if ((pos < 0) || (pos > arr.length))
			throw new IllegalArgumentException("Illegal offset.");
	}

	public TypesReader(byte[] arr, int off, int len)
	{
		this.arr = arr;
		this.pos = off;
		this.max = off + len;

		if ((pos < 0) || (pos > arr.length))
			throw new IllegalArgumentException("Illegal offset.");

		if ((max < 0) || (max > arr.length))
			throw new IllegalArgumentException("Illegal length.");
	}

	public int readByte() throws IOException
	{
		if (pos >= max)
			throw new IOException("Packet too short.");

		return (arr[pos++] & 0xff);
	}

	public byte[] readBytes(int len) throws IOException
	{
		if ((pos + len) > max)
			throw new IOException("Packet too short.");

		byte[] res = new byte[len];

		System.arraycopy(arr, pos, res, 0, len);
		pos += len;

		return res;
	}

	public void readBytes(byte[] dst, int off, int len) throws IOException
	{
		if ((pos + len) > max)
			throw new IOException("Packet too short.");

		System.arraycopy(arr, pos, dst, off, len);
		pos += len;
	}

	public boolean readBoolean() throws IOException
	{
		if (pos >= max)
			throw new IOException("Packet too short.");

		return (arr[pos++] != 0);
	}

	public int readUINT32() throws IOException
	{
		if ((pos + 4) > max)
			throw new IOException("Packet too short.");

		return ((arr[pos++] & 0xff) << 24) | ((arr[pos++] & 0xff) << 16) | ((arr[pos++] & 0xff) << 8)
				| (arr[pos++] & 0xff);
	}

	public long readUINT64() throws IOException
	{
		if ((pos + 8) > max)
			throw new IOException("Packet too short.");

		long high = ((arr[pos++] & 0xff) << 24) | ((arr[pos++] & 0xff) << 16) | ((arr[pos++] & 0xff) << 8)
				| (arr[pos++] & 0xff); /* sign extension may take place - will be shifted away =) */

		long low = ((arr[pos++] & 0xff) << 24) | ((arr[pos++] & 0xff) << 16) | ((arr[pos++] & 0xff) << 8)
				| (arr[pos++] & 0xff); /* sign extension may take place - handle below */

		return (high << 32) | (low & 0xffffffffl); /* see Java language spec (15.22.1, 5.6.2) */
	}

	public BigInteger readMPINT() throws IOException
	{
		BigInteger b;

		byte raw[] = readByteString();

		if (raw.length == 0)
			b = BigInteger.ZERO;
		else
			b = new BigInteger(raw);

		return b;
	}

	public byte[] readByteString() throws IOException
	{
		int len = readUINT32();

		if ((len + pos) > max)
			throw new IOException("Malformed SSH byte string.");

		byte[] res = new byte[len];
		System.arraycopy(arr, pos, res, 0, len);
		pos += len;
		return res;
	}

	public String readString(String charsetName) throws IOException
	{
		int len = readUINT32();

		if ((len + pos) > max)
			throw new IOException("Malformed SSH string.");

		String res = (charsetName == null) ? new String(arr, pos, len) : new String(arr, pos, len, charsetName);
		pos += len;

		return res;
	}

	public String readString() throws IOException
	{
		int len = readUINT32();

		if ((len + pos) > max)
			throw new IOException("Malformed SSH string.");

		String res = new String(arr, pos, len, "ISO-8859-1");
		
		pos += len;

		return res;
	}

	public String[] readNameList() throws IOException
	{
		return Tokenizer.parseTokens(readString(), ',');
	}

	public int remain()
	{
		return max - pos;
	}

}
