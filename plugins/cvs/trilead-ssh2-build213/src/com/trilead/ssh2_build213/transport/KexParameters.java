package com.trilead.ssh2_build213.transport;

/**
 * KexParameters.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: KexParameters.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public class KexParameters
{
	public byte[] cookie;
	public String[] kex_algorithms;
	public String[] server_host_key_algorithms;
	public String[] encryption_algorithms_client_to_server;
	public String[] encryption_algorithms_server_to_client;
	public String[] mac_algorithms_client_to_server;
	public String[] mac_algorithms_server_to_client;
	public String[] compression_algorithms_client_to_server;
	public String[] compression_algorithms_server_to_client;
	public String[] languages_client_to_server;
	public String[] languages_server_to_client;
	public boolean first_kex_packet_follows;
	public int reserved_field1;
}
