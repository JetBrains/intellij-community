
package com.trilead.ssh2.packets;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

/**
 * TypesWriter.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: TypesWriter.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */
public class TypesWriter
{
	byte arr[];
	int pos;

	public TypesWriter()
	{
		arr = new byte[256];
		pos = 0;
	}

	private void resize(int len)
	{
		byte new_arr[] = new byte[len];
		System.arraycopy(arr, 0, new_arr, 0, arr.length);
		arr = new_arr;
	}

	public int length()
	{
		return pos;
	}

	public byte[] getBytes()
	{
		byte[] dst = new byte[pos];
		System.arraycopy(arr, 0, dst, 0, pos);
		return dst;
	}

	public void getBytes(byte dst[])
	{
		System.arraycopy(arr, 0, dst, 0, pos);
	}

	public void writeUINT32(int val, int off)
	{
		if ((off + 4) > arr.length)
			resize(off + 32);

		arr[off++] = (byte) (val >> 24);
		arr[off++] = (byte) (val >> 16);
		arr[off++] = (byte) (val >> 8);
		arr[off++] = (byte) val;
	}

	public void writeUINT32(int val)
	{
		writeUINT32(val, pos);
		pos += 4;
	}

	public void writeUINT64(long val)
	{
		if ((pos + 8) > arr.length)
			resize(arr.length + 32);

		arr[pos++] = (byte) (val >> 56);
		arr[pos++] = (byte) (val >> 48);
		arr[pos++] = (byte) (val >> 40);
		arr[pos++] = (byte) (val >> 32);
		arr[pos++] = (byte) (val >> 24);
		arr[pos++] = (byte) (val >> 16);
		arr[pos++] = (byte) (val >> 8);
		arr[pos++] = (byte) val;
	}

	public void writeBoolean(boolean v)
	{
		if ((pos + 1) > arr.length)
			resize(arr.length + 32);

		arr[pos++] = v ? (byte) 1 : (byte) 0;
	}

	public void writeByte(int v, int off)
	{
		if ((off + 1) > arr.length)
			resize(off + 32);

		arr[off] = (byte) v;
	}

	public void writeByte(int v)
	{
		writeByte(v, pos);
		pos++;
	}

	public void writeMPInt(BigInteger b)
	{
		byte raw[] = b.toByteArray();

		if ((raw.length == 1) && (raw[0] == 0))
			writeUINT32(0); /* String with zero bytes of data */
		else
			writeString(raw, 0, raw.length);
	}

	public void writeBytes(byte[] buff)
	{
		writeBytes(buff, 0, buff.length);
	}

	public void writeBytes(byte[] buff, int off, int len)
	{
		if ((pos + len) > arr.length)
			resize(arr.length + len + 32);

		System.arraycopy(buff, off, arr, pos, len);
		pos += len;
	}

	public void writeString(byte[] buff, int off, int len)
	{
		writeUINT32(len);
		writeBytes(buff, off, len);
	}

	public void writeString(String v)
	{
		byte[] b;

		try
		{
			/* All Java JVMs must support ISO-8859-1 */
			b = v.getBytes("ISO-8859-1");
		}
		catch (UnsupportedEncodingException ignore)
		{
			b = v.getBytes();
		}

		writeUINT32(b.length);
		writeBytes(b, 0, b.length);
	}

	public void writeString(String v, String charsetName) throws UnsupportedEncodingException
	{
		byte[] b = (charsetName == null) ? v.getBytes() : v.getBytes(charsetName);

		writeUINT32(b.length);
		writeBytes(b, 0, b.length);
	}

	public void writeNameList(String v[])
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < v.length; i++)
		{
			if (i > 0)
				sb.append(',');
			sb.append(v[i]);
		}
		writeString(sb.toString());
	}
}
