package com.trilead.ssh2.packets;

import java.math.BigInteger;

/**
 * PacketKexDHInit.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketKexDHInit.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketKexDHInit
{
	byte[] payload;

	BigInteger e;

	public PacketKexDHInit(BigInteger e)
	{
		this.e = e;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_KEXDH_INIT);
			tw.writeMPInt(e);
			payload = tw.getBytes();
		}
		return payload;
	}
}
