
package com.trilead.ssh2_build213.crypto.digest;

import java.math.BigInteger;

/**
 * HashForSSH2Types.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: HashForSSH2Types.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public class HashForSSH2Types
{
	Digest md;

	public HashForSSH2Types(Digest md)
	{
		this.md = md;
	}

	public HashForSSH2Types(String type)
	{
		if (type.equals("SHA1"))
		{
			md = new SHA1();
		}
		else if (type.equals("MD5"))
		{
			md = new MD5();
		}
		else
			throw new IllegalArgumentException("Unknown algorithm " + type);
	}

	public void updateByte(byte b)
	{
		/* HACK - to test it with J2ME */
		byte[] tmp = new byte[1];
		tmp[0] = b;
		md.update(tmp);
	}

	public void updateBytes(byte[] b)
	{
		md.update(b);
	}

	public void updateUINT32(int v)
	{
		md.update((byte) (v >> 24));
		md.update((byte) (v >> 16));
		md.update((byte) (v >> 8));
		md.update((byte) (v));
	}

	public void updateByteString(byte[] b)
	{
		updateUINT32(b.length);
		updateBytes(b);
	}

	public void updateBigInt(BigInteger b)
	{
		updateByteString(b.toByteArray());
	}

	public void reset()
	{
		md.reset();
	}

	public int getDigestLength()
	{
		return md.getDigestLength();
	}

	public byte[] getDigest()
	{
		byte[] tmp = new byte[md.getDigestLength()];
		getDigest(tmp);
		return tmp;
	}

	public void getDigest(byte[] out)
	{
		getDigest(out, 0);
	}

	public void getDigest(byte[] out, int off)
	{
		md.digest(out, off);
	}
}
