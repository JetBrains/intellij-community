
package com.trilead.ssh2_build213.packets;

import com.trilead.ssh2_build213.DHGexParameters;

/**
 * PacketKexDhGexRequestOld.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketKexDhGexRequestOld.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketKexDhGexRequestOld
{
	byte[] payload;

	int n;

	public PacketKexDhGexRequestOld(DHGexParameters para)
	{
		this.n = para.getPref_group_len();
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_KEX_DH_GEX_REQUEST_OLD);
			tw.writeUINT32(n);
			payload = tw.getBytes();
		}
		return payload;
	}
}
