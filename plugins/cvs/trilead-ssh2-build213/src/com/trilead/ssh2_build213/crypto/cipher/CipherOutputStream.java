
package com.trilead.ssh2_build213.crypto.cipher;

import java.io.IOException;
import java.io.OutputStream;

/**
 * CipherOutputStream.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: CipherOutputStream.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class CipherOutputStream
{
	BlockCipher currentCipher;
	OutputStream bo;
	byte[] buffer;
	byte[] enc;
	int blockSize;
	int pos;

	/*
	 * We cannot use java.io.BufferedOutputStream, since that is not available
	 * in J2ME. Everything could be improved here alot.
	 */

	final int BUFF_SIZE = 2048;
	byte[] out_buffer = new byte[BUFF_SIZE];
	int out_buffer_pos = 0;

	public CipherOutputStream(BlockCipher tc, OutputStream bo)
	{
		this.bo = bo;
		changeCipher(tc);
	}

	private void internal_write(byte[] src, int off, int len) throws IOException
	{
		while (len > 0)
		{
			int space = BUFF_SIZE - out_buffer_pos;
			int copy = (len > space) ? space : len;

			System.arraycopy(src, off, out_buffer, out_buffer_pos, copy);

			off += copy;
			out_buffer_pos += copy;
			len -= copy;

			if (out_buffer_pos >= BUFF_SIZE)
			{
				bo.write(out_buffer, 0, BUFF_SIZE);
				out_buffer_pos = 0;
			}
		}
	}

	private void internal_write(int b) throws IOException
	{
		out_buffer[out_buffer_pos++] = (byte) b;
		if (out_buffer_pos >= BUFF_SIZE)
		{
			bo.write(out_buffer, 0, BUFF_SIZE);
			out_buffer_pos = 0;
		}
	}

	public void flush() throws IOException
	{
		if (pos != 0)
			throw new IOException("FATAL: cannot flush since crypto buffer is not aligned.");

		if (out_buffer_pos > 0)
		{
			bo.write(out_buffer, 0, out_buffer_pos);
			out_buffer_pos = 0;
		}
		bo.flush();
	}

	public void changeCipher(BlockCipher bc)
	{
		this.currentCipher = bc;
		blockSize = bc.getBlockSize();
		buffer = new byte[blockSize];
		enc = new byte[blockSize];
		pos = 0;
	}

	private void writeBlock() throws IOException
	{
		try
		{
			currentCipher.transformBlock(buffer, 0, enc, 0);
		}
		catch (Exception e)
		{
			throw (IOException) new IOException("Error while decrypting block.").initCause(e);
		}

		internal_write(enc, 0, blockSize);
		pos = 0;
	}

	public void write(byte[] src, int off, int len) throws IOException
	{
		while (len > 0)
		{
			int avail = blockSize - pos;
			int copy = Math.min(avail, len);

			System.arraycopy(src, off, buffer, pos, copy);
			pos += copy;
			off += copy;
			len -= copy;

			if (pos >= blockSize)
				writeBlock();
		}
	}

	public void write(int b) throws IOException
	{
		buffer[pos++] = (byte) b;
		if (pos >= blockSize)
			writeBlock();
	}

	public void writePlain(int b) throws IOException
	{
		if (pos != 0)
			throw new IOException("Cannot write plain since crypto buffer is not aligned.");
		internal_write(b);
	}

	public void writePlain(byte[] b, int off, int len) throws IOException
	{
		if (pos != 0)
			throw new IOException("Cannot write plain since crypto buffer is not aligned.");
		internal_write(b, off, len);
	}
}
