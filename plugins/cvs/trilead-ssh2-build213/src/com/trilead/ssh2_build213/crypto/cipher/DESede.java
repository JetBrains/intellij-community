
package com.trilead.ssh2_build213.crypto.cipher;

/*
 This file was shamelessly taken (and modified) from the Bouncy Castle Crypto package.
 Their licence file states the following:

 Copyright (c) 2000 - 2004 The Legion Of The Bouncy Castle
 (http://www.bouncycastle.org)

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE. 
 */

/**
 * DESede.
 * 
 * @author See comments in the source file
 * @version $Id: DESede.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 * 
 */
public class DESede extends DES
{
	private int[] key1 = null;
	private int[] key2 = null;
	private int[] key3 = null;

	private boolean encrypt;

	/**
	 * standard constructor.
	 */
	public DESede()
	{
	}

	/**
	 * initialise a DES cipher.
	 * 
	 * @param encrypting
	 *            whether or not we are for encryption.
	 * @param key
	 *            the parameters required to set up the cipher.
	 * @exception IllegalArgumentException
	 *                if the params argument is inappropriate.
	 */
	public void init(boolean encrypting, byte[] key)
	{
		key1 = generateWorkingKey(encrypting, key, 0);
		key2 = generateWorkingKey(!encrypting, key, 8);
		key3 = generateWorkingKey(encrypting, key, 16);

		encrypt = encrypting;
	}

	public String getAlgorithmName()
	{
		return "DESede";
	}

	public int getBlockSize()
	{
		return 8;
	}

	public void transformBlock(byte[] in, int inOff, byte[] out, int outOff)
	{
		if (key1 == null)
		{
			throw new IllegalStateException("DESede engine not initialised!");
		}

		if (encrypt)
		{
			desFunc(key1, in, inOff, out, outOff);
			desFunc(key2, out, outOff, out, outOff);
			desFunc(key3, out, outOff, out, outOff);
		}
		else
		{
			desFunc(key3, in, inOff, out, outOff);
			desFunc(key2, out, outOff, out, outOff);
			desFunc(key1, out, outOff, out, outOff);
		}
	}

	public void reset()
	{
	}
}
