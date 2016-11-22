
package com.trilead.ssh2_build213.crypto.dh;

import com.trilead.ssh2_build213.DHGexParameters;
import com.trilead.ssh2_build213.crypto.digest.HashForSSH2Types;

import java.math.BigInteger;
import java.security.SecureRandom;


/**
 * DhGroupExchange.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: DhGroupExchange.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public class DhGroupExchange
{
	/* Given by the standard */

	private BigInteger p;
	private BigInteger g;

	/* Client public and private */

	private BigInteger e;
	private BigInteger x;

	/* Server public */

	private BigInteger f;

	/* Shared secret */

	private BigInteger k;

	public DhGroupExchange(BigInteger p, BigInteger g)
	{
		this.p = p;
		this.g = g;
	}

	public void init(SecureRandom rnd)
	{
		k = null;

		x = new BigInteger(p.bitLength() - 1, rnd);
		e = g.modPow(x, p);
	}

	/**
	 * @return Returns the e.
	 */
	public BigInteger getE()
	{
		if (e == null)
			throw new IllegalStateException("Not initialized!");

		return e;
	}

	/**
	 * @return Returns the shared secret k.
	 */
	public BigInteger getK()
	{
		if (k == null)
			throw new IllegalStateException("Shared secret not yet known, need f first!");

		return k;
	}

	/**
	 * Sets f and calculates the shared secret.
	 */
	public void setF(BigInteger f)
	{
		if (e == null)
			throw new IllegalStateException("Not initialized!");

		BigInteger zero = BigInteger.valueOf(0);

		if (zero.compareTo(f) >= 0 || p.compareTo(f) <= 0)
			throw new IllegalArgumentException("Invalid f specified!");

		this.f = f;
		this.k = f.modPow(x, p);
	}

	public byte[] calculateH(byte[] clientversion, byte[] serverversion, byte[] clientKexPayload,
			byte[] serverKexPayload, byte[] hostKey, DHGexParameters para)
	{
		HashForSSH2Types hash = new HashForSSH2Types("SHA1");

		hash.updateByteString(clientversion);
		hash.updateByteString(serverversion);
		hash.updateByteString(clientKexPayload);
		hash.updateByteString(serverKexPayload);
		hash.updateByteString(hostKey);
		if (para.getMin_group_len() > 0)
			hash.updateUINT32(para.getMin_group_len());
		hash.updateUINT32(para.getPref_group_len());
		if (para.getMax_group_len() > 0)
			hash.updateUINT32(para.getMax_group_len());
		hash.updateBigInt(p);
		hash.updateBigInt(g);
		hash.updateBigInt(e);
		hash.updateBigInt(f);
		hash.updateBigInt(k);

		return hash.getDigest();
	}
}
