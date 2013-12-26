
package com.trilead.ssh2_build213.crypto.cipher;

import java.io.IOException;
import java.io.InputStream;

/**
 * CipherInputStream.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: CipherInputStream.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class CipherInputStream
{
	BlockCipher currentCipher;
	InputStream bi;
	byte[] buffer;
	byte[] enc;
	int blockSize;
	int pos;

	/*
	 * We cannot use java.io.BufferedInputStream, since that is not available in
	 * J2ME. Everything could be improved alot here.
	 */

	final int BUFF_SIZE = 2048;
	byte[] input_buffer = new byte[BUFF_SIZE];
	int input_buffer_pos = 0;
	int input_buffer_size = 0;

	public CipherInputStream(BlockCipher tc, InputStream bi)
	{
		this.bi = bi;
		changeCipher(tc);
	}

	private int fill_buffer() throws IOException
	{
		input_buffer_pos = 0;
		input_buffer_size = bi.read(input_buffer, 0, BUFF_SIZE);
		return input_buffer_size;
	}

	private int internal_read(byte[] b, int off, int len) throws IOException
	{
		if (input_buffer_size < 0)
			return -1;

		if (input_buffer_pos >= input_buffer_size)
		{
			if (fill_buffer() <= 0)
				return -1;
		}
		
		int avail = input_buffer_size - input_buffer_pos;
		int thiscopy = (len > avail) ? avail : len;

		System.arraycopy(input_buffer, input_buffer_pos, b, off, thiscopy);
		input_buffer_pos += thiscopy;

		return thiscopy;
	}

	public void changeCipher(BlockCipher bc)
	{
		this.currentCipher = bc;
		blockSize = bc.getBlockSize();
		buffer = new byte[blockSize];
		enc = new byte[blockSize];
		pos = blockSize;
	}

	private void getBlock() throws IOException
	{
		int n = 0;
		while (n < blockSize)
		{
			int len = internal_read(enc, n, blockSize - n);
			if (len < 0)
				throw new IOException("Cannot read full block, EOF reached.");
			n += len;
		}

		try
		{
			currentCipher.transformBlock(enc, 0, buffer, 0);
		}
		catch (Exception e)
		{
			throw new IOException("Error while decrypting block.");
		}
		pos = 0;
	}

	public int read(byte[] dst) throws IOException
	{
		return read(dst, 0, dst.length);
	}

	public int read(byte[] dst, int off, int len) throws IOException
	{
		int count = 0;

		while (len > 0)
		{
			if (pos >= blockSize)
				getBlock();

			int avail = blockSize - pos;
			int copy = Math.min(avail, len);
			System.arraycopy(buffer, pos, dst, off, copy);
			pos += copy;
			off += copy;
			len -= copy;
			count += copy;
		}
		return count;
	}

	public int read() throws IOException
	{
		if (pos >= blockSize)
		{
			getBlock();
		}
		return buffer[pos++] & 0xff;
	}

	public int readPlain(byte[] b, int off, int len) throws IOException
	{
		if (pos != blockSize)
			throw new IOException("Cannot read plain since crypto buffer is not aligned.");
		int n = 0;
		while (n < len)
		{
			int cnt = internal_read(b, off + n, len - n);
			if (cnt < 0)
				throw new IOException("Cannot fill buffer, EOF reached.");
			n += cnt;
		}
		return n;
	}
}
