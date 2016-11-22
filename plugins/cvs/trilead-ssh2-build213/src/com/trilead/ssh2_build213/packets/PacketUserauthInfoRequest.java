
package com.trilead.ssh2_build213.packets;

import java.io.IOException;

/**
 * PacketUserauthInfoRequest.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketUserauthInfoRequest.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketUserauthInfoRequest
{
	byte[] payload;

	String name;
	String instruction;
	String languageTag;
	int numPrompts;

	String prompt[];
	boolean echo[];

	public PacketUserauthInfoRequest(byte payload[], int off, int len) throws IOException
	{
		this.payload = new byte[len];
		System.arraycopy(payload, off, this.payload, 0, len);

		TypesReader tr = new TypesReader(payload, off, len);

		int packet_type = tr.readByte();

		if (packet_type != Packets.SSH_MSG_USERAUTH_INFO_REQUEST)
			throw new IOException("This is not a SSH_MSG_USERAUTH_INFO_REQUEST! (" + packet_type + ")");

		name = tr.readString();
		instruction = tr.readString();
		languageTag = tr.readString();

		numPrompts = tr.readUINT32();

		prompt = new String[numPrompts];
		echo = new boolean[numPrompts];

		for (int i = 0; i < numPrompts; i++)
		{
			prompt[i] = tr.readString();
			echo[i] = tr.readBoolean();
		}

		if (tr.remain() != 0)
			throw new IOException("Padding in SSH_MSG_USERAUTH_INFO_REQUEST packet!");
	}

	public boolean[] getEcho()
	{
		return echo;
	}

	public String getInstruction()
	{
		return instruction;
	}

	public String getLanguageTag()
	{
		return languageTag;
	}

	public String getName()
	{
		return name;
	}

	public int getNumPrompts()
	{
		return numPrompts;
	}

	public String[] getPrompt()
	{
		return prompt;
	}
}
