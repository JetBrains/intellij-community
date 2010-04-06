
package com.trilead.ssh2.packets;

/**
 * PacketChannelTrileadPing.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketChannelTrileadPing.java,v 1.1 2008/03/03 07:01:36
 *          cplattne Exp $
 */
public class PacketChannelTrileadPing
{
	byte[] payload;

	public int recipientChannelID;

	public PacketChannelTrileadPing(int recipientChannelID)
	{
		this.recipientChannelID = recipientChannelID;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_CHANNEL_REQUEST);
			tw.writeUINT32(recipientChannelID);
			tw.writeString("trilead-ping");
			tw.writeBoolean(true);
			payload = tw.getBytes();
		}
		return payload;
	}
}
