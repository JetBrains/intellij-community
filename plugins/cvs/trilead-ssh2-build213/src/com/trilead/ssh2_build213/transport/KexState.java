package com.trilead.ssh2_build213.transport;


import com.trilead.ssh2_build213.DHGexParameters;
import com.trilead.ssh2_build213.crypto.dh.DhExchange;
import com.trilead.ssh2_build213.crypto.dh.DhGroupExchange;
import com.trilead.ssh2_build213.packets.PacketKexInit;

import java.math.BigInteger;

/**
 * KexState.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: KexState.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public class KexState
{
	public PacketKexInit localKEX;
	public PacketKexInit remoteKEX;
	public NegotiatedParameters np;
	public int state = 0;

	public BigInteger K;
	public byte[] H;
	
	public byte[] hostkey;
	
	public DhExchange dhx;
	public DhGroupExchange dhgx;
	public DHGexParameters dhgexParameters;
}
