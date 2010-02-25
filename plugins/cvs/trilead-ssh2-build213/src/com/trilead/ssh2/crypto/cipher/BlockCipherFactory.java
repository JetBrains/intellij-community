
package com.trilead.ssh2.crypto.cipher;

import java.util.Vector;

/**
 * BlockCipherFactory.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: BlockCipherFactory.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */
public class BlockCipherFactory
{
	static class CipherEntry
	{
		String type;
		int blocksize;
		int keysize;
		String cipherClass;

		public CipherEntry(String type, int blockSize, int keySize, String cipherClass)
		{
			this.type = type;
			this.blocksize = blockSize;
			this.keysize = keySize;
			this.cipherClass = cipherClass;
		}
	}

	static Vector ciphers = new Vector();

	static
	{
		/* Higher Priority First */

		ciphers.addElement(new CipherEntry("aes256-ctr", 16, 32, "com.trilead.ssh2.crypto.cipher.AES"));
		ciphers.addElement(new CipherEntry("aes192-ctr", 16, 24, "com.trilead.ssh2.crypto.cipher.AES"));
		ciphers.addElement(new CipherEntry("aes128-ctr", 16, 16, "com.trilead.ssh2.crypto.cipher.AES"));
		ciphers.addElement(new CipherEntry("blowfish-ctr", 8, 16, "com.trilead.ssh2.crypto.cipher.BlowFish"));

		ciphers.addElement(new CipherEntry("aes256-cbc", 16, 32, "com.trilead.ssh2.crypto.cipher.AES"));
		ciphers.addElement(new CipherEntry("aes192-cbc", 16, 24, "com.trilead.ssh2.crypto.cipher.AES"));
		ciphers.addElement(new CipherEntry("aes128-cbc", 16, 16, "com.trilead.ssh2.crypto.cipher.AES"));
		ciphers.addElement(new CipherEntry("blowfish-cbc", 8, 16, "com.trilead.ssh2.crypto.cipher.BlowFish"));
		
		ciphers.addElement(new CipherEntry("3des-ctr", 8, 24, "com.trilead.ssh2.crypto.cipher.DESede"));
		ciphers.addElement(new CipherEntry("3des-cbc", 8, 24, "com.trilead.ssh2.crypto.cipher.DESede"));
	}

	public static String[] getDefaultCipherList()
	{
		String list[] = new String[ciphers.size()];
		for (int i = 0; i < ciphers.size(); i++)
		{
			CipherEntry ce = (CipherEntry) ciphers.elementAt(i);
			list[i] = new String(ce.type);
		}
		return list;
	}

	public static void checkCipherList(String[] cipherCandidates)
	{
		for (int i = 0; i < cipherCandidates.length; i++)
			getEntry(cipherCandidates[i]);
	}

	public static BlockCipher createCipher(String type, boolean encrypt, byte[] key, byte[] iv)
	{
		try
		{
			CipherEntry ce = getEntry(type);
			Class cc = Class.forName(ce.cipherClass);
			BlockCipher bc = (BlockCipher) cc.newInstance();

			if (type.endsWith("-cbc"))
			{
				bc.init(encrypt, key);
				return new CBCMode(bc, iv, encrypt);
			}
			else if (type.endsWith("-ctr"))
			{
				bc.init(true, key);
				return new CTRMode(bc, iv, encrypt);
			}
			throw new IllegalArgumentException("Cannot instantiate " + type);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Cannot instantiate " + type);
		}
	}

	private static CipherEntry getEntry(String type)
	{
		for (int i = 0; i < ciphers.size(); i++)
		{
			CipherEntry ce = (CipherEntry) ciphers.elementAt(i);
			if (ce.type.equals(type))
				return ce;
		}
		throw new IllegalArgumentException("Unkown algorithm " + type);
	}

	public static int getBlockSize(String type)
	{
		CipherEntry ce = getEntry(type);
		return ce.blocksize;
	}

	public static int getKeySize(String type)
	{
		CipherEntry ce = getEntry(type);
		return ce.keysize;
	}
}
