package com.trilead.ssh2.crypto.cipher;

/**
 * BlockCipher.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: BlockCipher.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public interface BlockCipher
{
	public void init(boolean forEncryption, byte[] key);

	public int getBlockSize();

	public void transformBlock(byte[] src, int srcoff, byte[] dst, int dstoff);
}
