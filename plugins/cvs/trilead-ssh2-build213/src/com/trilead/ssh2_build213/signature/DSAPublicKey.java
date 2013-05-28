package com.trilead.ssh2_build213.signature;

import java.math.BigInteger;

/**
 * DSAPublicKey.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: DSAPublicKey.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public class DSAPublicKey
{
	private BigInteger p;
	private BigInteger q;
	private BigInteger g;
	private BigInteger y;

	public DSAPublicKey(BigInteger p, BigInteger q, BigInteger g, BigInteger y)
	{
		this.p = p;
		this.q = q;
		this.g = g;
		this.y = y;
	}

	public BigInteger getP()
	{
		return p;
	}

	public BigInteger getQ()
	{
		return q;
	}

	public BigInteger getG()
	{
		return g;
	}

	public BigInteger getY()
	{
		return y;
	}
}