
package com.trilead.ssh2_build213.signature;

import java.math.BigInteger;


/**
 * RSASignature.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: RSASignature.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */

public class RSASignature
{
	BigInteger s;

	public BigInteger getS()
	{
		return s;
	}

	public RSASignature(BigInteger s)
	{
		this.s = s;
	}
}