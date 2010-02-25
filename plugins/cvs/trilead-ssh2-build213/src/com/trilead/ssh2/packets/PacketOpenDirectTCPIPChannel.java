package com.trilead.ssh2.packets;


/**
 * PacketOpenDirectTCPIPChannel.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketOpenDirectTCPIPChannel.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketOpenDirectTCPIPChannel
{
	byte[] payload;

	int channelID;
	int initialWindowSize;
	int maxPacketSize;

	String host_to_connect;
	int port_to_connect;
	String originator_IP_address;
	int originator_port;

	public PacketOpenDirectTCPIPChannel(int channelID, int initialWindowSize, int maxPacketSize,
			String host_to_connect, int port_to_connect, String originator_IP_address,
			int originator_port)
	{
		this.channelID = channelID;
		this.initialWindowSize = initialWindowSize;
		this.maxPacketSize = maxPacketSize;
		this.host_to_connect = host_to_connect;
		this.port_to_connect = port_to_connect;
		this.originator_IP_address = originator_IP_address;
		this.originator_port = originator_port;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();

			tw.writeByte(Packets.SSH_MSG_CHANNEL_OPEN);
			tw.writeString("direct-tcpip");
			tw.writeUINT32(channelID);
			tw.writeUINT32(initialWindowSize);
			tw.writeUINT32(maxPacketSize);
			tw.writeString(host_to_connect);
			tw.writeUINT32(port_to_connect);
			tw.writeString(originator_IP_address);
			tw.writeUINT32(originator_port);

			payload = tw.getBytes();
		}
		return payload;
	}
}
