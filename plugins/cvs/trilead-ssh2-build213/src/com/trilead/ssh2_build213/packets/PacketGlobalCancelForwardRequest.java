
package com.trilead.ssh2_build213.packets;

/**
 * PacketGlobalCancelForwardRequest.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketGlobalCancelForwardRequest.java,v 1.1 2007/10/15 12:49:55
 *          cplattne Exp $
 */
public class PacketGlobalCancelForwardRequest
{
	byte[] payload;

	public boolean wantReply;
	public String bindAddress;
	public int bindPort;

	public PacketGlobalCancelForwardRequest(boolean wantReply, String bindAddress, int bindPort)
	{
		this.wantReply = wantReply;
		this.bindAddress = bindAddress;
		this.bindPort = bindPort;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_GLOBAL_REQUEST);

			tw.writeString("cancel-tcpip-forward");
			tw.writeBoolean(wantReply);
			tw.writeString(bindAddress);
			tw.writeUINT32(bindPort);

			payload = tw.getBytes();
		}
		return payload;
	}
}
