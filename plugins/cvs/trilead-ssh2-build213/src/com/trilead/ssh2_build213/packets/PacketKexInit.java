
package com.trilead.ssh2_build213.packets;

import com.trilead.ssh2_build213.crypto.CryptoWishList;
import com.trilead.ssh2_build213.transport.KexParameters;

import java.io.IOException;
import java.security.SecureRandom;


/**
 * PacketKexInit.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketKexInit.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketKexInit
{
	byte[] payload;

	KexParameters kp = new KexParameters();

	public PacketKexInit(CryptoWishList cwl, SecureRandom rnd)
	{
		kp.cookie = new byte[16];
		rnd.nextBytes(kp.cookie);

		kp.kex_algorithms = cwl.kexAlgorithms;
		kp.server_host_key_algorithms = cwl.serverHostKeyAlgorithms;
		kp.encryption_algorithms_client_to_server = cwl.c2s_enc_algos;
		kp.encryption_algorithms_server_to_client = cwl.s2c_enc_algos;
		kp.mac_algorithms_client_to_server = cwl.c2s_mac_algos;
		kp.mac_algorithms_server_to_client = cwl.s2c_mac_algos;
		kp.compression_algorithms_client_to_server = new String[] { "none" };
		kp.compression_algorithms_server_to_client = new String[] { "none" };
		kp.languages_client_to_server = new String[] {};
		kp.languages_server_to_client = new String[] {};
		kp.first_kex_packet_follows = false;
		kp.reserved_field1 = 0;
	}

	public PacketKexInit(byte payload[], int off, int len) throws IOException
	{
		this.payload = new byte[len];
		System.arraycopy(payload, off, this.payload, 0, len);

		TypesReader tr = new TypesReader(payload, off, len);

		int packet_type = tr.readByte();

		if (packet_type != Packets.SSH_MSG_KEXINIT)
			throw new IOException("This is not a KexInitPacket! (" + packet_type + ")");

		kp.cookie = tr.readBytes(16);
		kp.kex_algorithms = tr.readNameList();
		kp.server_host_key_algorithms = tr.readNameList();
		kp.encryption_algorithms_client_to_server = tr.readNameList();
		kp.encryption_algorithms_server_to_client = tr.readNameList();
		kp.mac_algorithms_client_to_server = tr.readNameList();
		kp.mac_algorithms_server_to_client = tr.readNameList();
		kp.compression_algorithms_client_to_server = tr.readNameList();
		kp.compression_algorithms_server_to_client = tr.readNameList();
		kp.languages_client_to_server = tr.readNameList();
		kp.languages_server_to_client = tr.readNameList();
		kp.first_kex_packet_follows = tr.readBoolean();
		kp.reserved_field1 = tr.readUINT32();

		if (tr.remain() != 0)
			throw new IOException("Padding in KexInitPacket!");
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_KEXINIT);
			tw.writeBytes(kp.cookie, 0, 16);
			tw.writeNameList(kp.kex_algorithms);
			tw.writeNameList(kp.server_host_key_algorithms);
			tw.writeNameList(kp.encryption_algorithms_client_to_server);
			tw.writeNameList(kp.encryption_algorithms_server_to_client);
			tw.writeNameList(kp.mac_algorithms_client_to_server);
			tw.writeNameList(kp.mac_algorithms_server_to_client);
			tw.writeNameList(kp.compression_algorithms_client_to_server);
			tw.writeNameList(kp.compression_algorithms_server_to_client);
			tw.writeNameList(kp.languages_client_to_server);
			tw.writeNameList(kp.languages_server_to_client);
			tw.writeBoolean(kp.first_kex_packet_follows);
			tw.writeUINT32(kp.reserved_field1);
			payload = tw.getBytes();
		}
		return payload;
	}

	public KexParameters getKexParameters()
	{
		return kp;
	}

	public String[] getCompression_algorithms_client_to_server()
	{
		return kp.compression_algorithms_client_to_server;
	}

	public String[] getCompression_algorithms_server_to_client()
	{
		return kp.compression_algorithms_server_to_client;
	}

	public byte[] getCookie()
	{
		return kp.cookie;
	}

	public String[] getEncryption_algorithms_client_to_server()
	{
		return kp.encryption_algorithms_client_to_server;
	}

	public String[] getEncryption_algorithms_server_to_client()
	{
		return kp.encryption_algorithms_server_to_client;
	}

	public boolean isFirst_kex_packet_follows()
	{
		return kp.first_kex_packet_follows;
	}

	public String[] getKex_algorithms()
	{
		return kp.kex_algorithms;
	}

	public String[] getLanguages_client_to_server()
	{
		return kp.languages_client_to_server;
	}

	public String[] getLanguages_server_to_client()
	{
		return kp.languages_server_to_client;
	}

	public String[] getMac_algorithms_client_to_server()
	{
		return kp.mac_algorithms_client_to_server;
	}

	public String[] getMac_algorithms_server_to_client()
	{
		return kp.mac_algorithms_server_to_client;
	}

	public int getReserved_field1()
	{
		return kp.reserved_field1;
	}

	public String[] getServer_host_key_algorithms()
	{
		return kp.server_host_key_algorithms;
	}
}
