package com.trilead.ssh2_build213.packets;

import java.io.IOException;
import java.math.BigInteger;

/**
 * PacketKexDhGexGroup.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketKexDhGexGroup.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketKexDhGexGroup
{
	byte[] payload;

	BigInteger p;
	BigInteger g;

	public PacketKexDhGexGroup(byte payload[], int off, int len) throws IOException
	{
		this.payload = new byte[len];
		System.arraycopy(payload, off, this.payload, 0, len);

		TypesReader tr = new TypesReader(payload, off, len);

		int packet_type = tr.readByte();

		if (packet_type != Packets.SSH_MSG_KEX_DH_GEX_GROUP)
			throw new IllegalArgumentException(
					"This is not a SSH_MSG_KEX_DH_GEX_GROUP! (" + packet_type
							+ ")");

		p = tr.readMPINT();
		g = tr.readMPINT();

		if (tr.remain() != 0)
			throw new IOException("PADDING IN SSH_MSG_KEX_DH_GEX_GROUP!");
	}

	public BigInteger getG()
	{
		return g;
	}

	public BigInteger getP()
	{
		return p;
	}
}
