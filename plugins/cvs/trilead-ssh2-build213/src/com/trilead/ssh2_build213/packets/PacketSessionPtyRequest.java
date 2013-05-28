package com.trilead.ssh2_build213.packets;


/**
 * PacketSessionPtyRequest.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketSessionPtyRequest.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketSessionPtyRequest
{
	byte[] payload;

	public int recipientChannelID;
	public boolean wantReply;
	public String term;
	public int character_width;
	public int character_height;
	public int pixel_width;
	public int pixel_height;
	public byte[] terminal_modes;

	public PacketSessionPtyRequest(int recipientChannelID, boolean wantReply, String term,
			int character_width, int character_height, int pixel_width, int pixel_height,
			byte[] terminal_modes)
	{
		this.recipientChannelID = recipientChannelID;
		this.wantReply = wantReply;
		this.term = term;
		this.character_width = character_width;
		this.character_height = character_height;
		this.pixel_width = pixel_width;
		this.pixel_height = pixel_height;
		this.terminal_modes = terminal_modes;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_CHANNEL_REQUEST);
			tw.writeUINT32(recipientChannelID);
			tw.writeString("pty-req");
			tw.writeBoolean(wantReply);
			tw.writeString(term);
			tw.writeUINT32(character_width);
			tw.writeUINT32(character_height);
			tw.writeUINT32(pixel_width);
			tw.writeUINT32(pixel_height);
			tw.writeString(terminal_modes, 0, terminal_modes.length);

			payload = tw.getBytes();
		}
		return payload;
	}
}
