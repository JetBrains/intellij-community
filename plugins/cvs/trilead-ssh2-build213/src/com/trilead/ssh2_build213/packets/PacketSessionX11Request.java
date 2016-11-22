
package com.trilead.ssh2_build213.packets;

/**
 * PacketSessionX11Request.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketSessionX11Request.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketSessionX11Request
{
	byte[] payload;

	public int recipientChannelID;
	public boolean wantReply;

	public boolean singleConnection;
	String x11AuthenticationProtocol;
	String x11AuthenticationCookie;
	int x11ScreenNumber;

	public PacketSessionX11Request(int recipientChannelID, boolean wantReply, boolean singleConnection,
			String x11AuthenticationProtocol, String x11AuthenticationCookie, int x11ScreenNumber)
	{
		this.recipientChannelID = recipientChannelID;
		this.wantReply = wantReply;

		this.singleConnection = singleConnection;
		this.x11AuthenticationProtocol = x11AuthenticationProtocol;
		this.x11AuthenticationCookie = x11AuthenticationCookie;
		this.x11ScreenNumber = x11ScreenNumber;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_CHANNEL_REQUEST);
			tw.writeUINT32(recipientChannelID);
			tw.writeString("x11-req");
			tw.writeBoolean(wantReply);

			tw.writeBoolean(singleConnection);
			tw.writeString(x11AuthenticationProtocol);
			tw.writeString(x11AuthenticationCookie);
			tw.writeUINT32(x11ScreenNumber);

			payload = tw.getBytes();
		}
		return payload;
	}
}
