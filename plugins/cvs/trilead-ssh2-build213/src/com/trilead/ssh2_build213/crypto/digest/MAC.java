
package com.trilead.ssh2_build213.crypto.digest;

/**
 * MAC.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: MAC.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public final class MAC
{
	Digest mac;
	int size;

	public final static String[] getMacList()
	{
		/* Higher Priority First */

		return new String[] { "hmac-sha1-96", "hmac-sha1", "hmac-md5-96", "hmac-md5" };
	}

	public final static void checkMacList(String[] macs)
	{
		for (int i = 0; i < macs.length; i++)
			getKeyLen(macs[i]);
	}

	public final static int getKeyLen(String type)
	{
		if (type.equals("hmac-sha1"))
			return 20;
		if (type.equals("hmac-sha1-96"))
			return 20;
		if (type.equals("hmac-md5"))
			return 16;
		if (type.equals("hmac-md5-96"))
			return 16;
		throw new IllegalArgumentException("Unkown algorithm " + type);
	}

	public MAC(String type, byte[] key)
	{
		if (type.equals("hmac-sha1"))
		{
			mac = new HMAC(new SHA1(), key, 20);
		}
		else if (type.equals("hmac-sha1-96"))
		{
			mac = new HMAC(new SHA1(), key, 12);
		}
		else if (type.equals("hmac-md5"))
		{
			mac = new HMAC(new MD5(), key, 16);
		}
		else if (type.equals("hmac-md5-96"))
		{
			mac = new HMAC(new MD5(), key, 12);
		}
		else
			throw new IllegalArgumentException("Unkown algorithm " + type);

		size = mac.getDigestLength();
	}

	public final void initMac(int seq)
	{
		mac.reset();
		mac.update((byte) (seq >> 24));
		mac.update((byte) (seq >> 16));
		mac.update((byte) (seq >> 8));
		mac.update((byte) (seq));
	}

	public final void update(byte[] packetdata, int off, int len)
	{
		mac.update(packetdata, off, len);
	}

	public final void getMac(byte[] out, int off)
	{
		mac.digest(out, off);
	}

	public final int size()
	{
		return size;
	}
}
