
package com.trilead.ssh2_build213.packets;

/**
 * PacketGlobalTrileadPing.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketGlobalTrileadPing.java,v 1.1 2008/03/03 07:01:36 cplattne Exp $
 */
public class PacketGlobalTrileadPing
{
	byte[] payload;

	public PacketGlobalTrileadPing()
	{
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_GLOBAL_REQUEST);
			
			tw.writeString("trilead-ping");
			tw.writeBoolean(true);

			payload = tw.getBytes();
		}
		return payload;
	}
}
