package com.trilead.ssh2.crypto.cipher;

/**
 * CBCMode.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: CBCMode.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class CBCMode implements BlockCipher
{
	BlockCipher tc;
	int blockSize;
	boolean doEncrypt;

	byte[] cbc_vector;
	byte[] tmp_vector;

	public void init(boolean forEncryption, byte[] key)
	{
	}
	
	public CBCMode(BlockCipher tc, byte[] iv, boolean doEncrypt)
			throws IllegalArgumentException
	{
		this.tc = tc;
		this.blockSize = tc.getBlockSize();
		this.doEncrypt = doEncrypt;

		if (this.blockSize != iv.length)
			throw new IllegalArgumentException("IV must be " + blockSize
					+ " bytes long! (currently " + iv.length + ")");

		this.cbc_vector = new byte[blockSize];
		this.tmp_vector = new byte[blockSize];
		System.arraycopy(iv, 0, cbc_vector, 0, blockSize);
	}

	public int getBlockSize()
	{
		return blockSize;
	}

	private void encryptBlock(byte[] src, int srcoff, byte[] dst, int dstoff)
	{
		for (int i = 0; i < blockSize; i++)
			cbc_vector[i] ^= src[srcoff + i];

		tc.transformBlock(cbc_vector, 0, dst, dstoff);

		System.arraycopy(dst, dstoff, cbc_vector, 0, blockSize);
	}

	private void decryptBlock(byte[] src, int srcoff, byte[] dst, int dstoff)
	{
		/* Assume the worst, src and dst are overlapping... */
		
		System.arraycopy(src, srcoff, tmp_vector, 0, blockSize);
		
		tc.transformBlock(src, srcoff, dst, dstoff);
		
		for (int i = 0; i < blockSize; i++)
			dst[dstoff + i] ^= cbc_vector[i];

		/* ...that is why we need a tmp buffer. */
		
		byte[] swap = cbc_vector;
		cbc_vector = tmp_vector;
		tmp_vector = swap;
	}

	public void transformBlock(byte[] src, int srcoff, byte[] dst, int dstoff)
	{
		if (doEncrypt)
			encryptBlock(src, srcoff, dst, dstoff);
		else
			decryptBlock(src, srcoff, dst, dstoff);
	}
}
