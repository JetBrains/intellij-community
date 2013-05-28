package com.trilead.ssh2_build213.packets;

import java.io.IOException;

/**
 * PacketChannelOpenConfirmation.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketChannelOpenConfirmation.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketChannelOpenConfirmation
{
	byte[] payload;

	public int recipientChannelID;
	public int senderChannelID;
	public int initialWindowSize;
	public int maxPacketSize;

	public PacketChannelOpenConfirmation(int recipientChannelID, int senderChannelID, int initialWindowSize,
			int maxPacketSize)
	{
		this.recipientChannelID = recipientChannelID;
		this.senderChannelID = senderChannelID;
		this.initialWindowSize = initialWindowSize;
		this.maxPacketSize = maxPacketSize;
	}

	public PacketChannelOpenConfirmation(byte payload[], int off, int len) throws IOException
	{
		this.payload = new byte[len];
		System.arraycopy(payload, off, this.payload, 0, len);

		TypesReader tr = new TypesReader(payload, off, len);

		int packet_type = tr.readByte();

		if (packet_type != Packets.SSH_MSG_CHANNEL_OPEN_CONFIRMATION)
			throw new IOException(
					"This is not a SSH_MSG_CHANNEL_OPEN_CONFIRMATION! ("
							+ packet_type + ")");

		recipientChannelID = tr.readUINT32();
		senderChannelID = tr.readUINT32();
		initialWindowSize = tr.readUINT32();
		maxPacketSize = tr.readUINT32();
		
		if (tr.remain() != 0)
			throw new IOException("Padding in SSH_MSG_CHANNEL_OPEN_CONFIRMATION packet!");
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_CHANNEL_OPEN_CONFIRMATION);
			tw.writeUINT32(recipientChannelID);
			tw.writeUINT32(senderChannelID);
			tw.writeUINT32(initialWindowSize);
			tw.writeUINT32(maxPacketSize);
			payload = tw.getBytes();
		}
		return payload;
	}
}
