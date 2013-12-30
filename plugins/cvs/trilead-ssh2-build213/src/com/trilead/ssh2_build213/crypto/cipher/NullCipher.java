package com.trilead.ssh2_build213.crypto.cipher;

/**
 * NullCipher.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: NullCipher.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class NullCipher implements BlockCipher
{
	private int blockSize = 8;
	
	public NullCipher()
	{
	}

	public NullCipher(int blockSize)
	{
		this.blockSize = blockSize;
	}
	
	public void init(boolean forEncryption, byte[] key)
	{
	}

	public int getBlockSize()
	{
		return blockSize;
	}

	public void transformBlock(byte[] src, int srcoff, byte[] dst, int dstoff)
	{
		System.arraycopy(src, srcoff, dst, dstoff, blockSize);
	}
}
