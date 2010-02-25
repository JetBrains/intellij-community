package com.trilead.ssh2.packets;


/**
 * PacketSessionSubsystemRequest.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketSessionSubsystemRequest.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketSessionSubsystemRequest
{
	byte[] payload;

	public int recipientChannelID;
	public boolean wantReply;
	public String subsystem;

	public PacketSessionSubsystemRequest(int recipientChannelID, boolean wantReply, String subsystem)
	{
		this.recipientChannelID = recipientChannelID;
		this.wantReply = wantReply;
		this.subsystem = subsystem;
	}
	
	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_CHANNEL_REQUEST);
			tw.writeUINT32(recipientChannelID);
			tw.writeString("subsystem");
			tw.writeBoolean(wantReply);
			tw.writeString(subsystem);
			payload = tw.getBytes();
			tw.getBytes(payload);
		}
		return payload;
	}
}
