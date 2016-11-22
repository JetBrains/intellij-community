
package com.trilead.ssh2_build213;

import com.trilead.ssh2_build213.auth.AuthenticationManager;
import com.trilead.ssh2_build213.channel.ChannelManager;
import com.trilead.ssh2_build213.crypto.CryptoWishList;
import com.trilead.ssh2_build213.crypto.cipher.BlockCipherFactory;
import com.trilead.ssh2_build213.crypto.digest.MAC;
import com.trilead.ssh2_build213.log.Logger;
import com.trilead.ssh2_build213.packets.PacketIgnore;
import com.trilead.ssh2_build213.transport.ClientServerHello;
import com.trilead.ssh2_build213.transport.KexManager;
import com.trilead.ssh2_build213.transport.TransportManager;
import com.trilead.ssh2_build213.util.TimeoutService;
import com.trilead.ssh2_build213.util.TimeoutService.TimeoutToken;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Vector;

/**
 * A <code>Connection</code> is used to establish an encrypted TCP/IP
 * connection to a SSH-2 server.
 * <p>
 * Typically, one
 * <ol>
 * <li>creates a {@link #Connection(String) Connection} object.</li>
 * <li>calls the {@link #connect() connect()} method.</li>
 * <li>calls some of the authentication methods (e.g.,
 * {@link #authenticateWithPublicKey(String, File, String) authenticateWithPublicKey()}).</li>
 * <li>calls one or several times the {@link #openSession() openSession()}
 * method.</li>
 * <li>finally, one must close the connection and release resources with the
 * {@link #close() close()} method.</li>
 * </ol>
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: Connection.java,v 1.3 2008/04/01 12:38:09 cplattne Exp $
 */

public class Connection
{
	/**
	 * The identifier presented to the SSH-2 server.
	 */
	public final static String identification = "TrileadSSH2Java_213";

	/**
	 * Will be used to generate all random data needed for the current
	 * connection. Note: SecureRandom.nextBytes() is thread safe.
	 */
	private SecureRandom generator;

	/**
	 * Unless you know what you are doing, you will never need this.
	 * 
	 * @return The list of supported cipher algorithms by this implementation.
	 */
	public static synchronized String[] getAvailableCiphers()
	{
		return BlockCipherFactory.getDefaultCipherList();
	}

	/**
	 * Unless you know what you are doing, you will never need this.
	 * 
	 * @return The list of supported MAC algorthims by this implementation.
	 */
	public static synchronized String[] getAvailableMACs()
	{
		return MAC.getMacList();
	}

	/**
	 * Unless you know what you are doing, you will never need this.
	 * 
	 * @return The list of supported server host key algorthims by this
	 *         implementation.
	 */
	public static synchronized String[] getAvailableServerHostKeyAlgorithms()
	{
		return KexManager.getDefaultServerHostkeyAlgorithmList();
	}

	private AuthenticationManager am;

	private boolean authenticated = false;
	private ChannelManager cm;

	private CryptoWishList cryptoWishList = new CryptoWishList();

	private DHGexParameters dhgexpara = new DHGexParameters();

	private final String hostname;

	private final int port;

	private TransportManager tm;

	private boolean tcpNoDelay = false;

	private ProxyData proxyData = null;

	private Vector connectionMonitors = new Vector();

	/**
	 * Prepares a fresh <code>Connection</code> object which can then be used
	 * to establish a connection to the specified SSH-2 server.
	 * <p>
	 * Same as {@link #Connection(String, int) Connection(hostname, 22)}.
	 * 
	 * @param hostname
	 *            the hostname of the SSH-2 server.
	 */
	public Connection(String hostname)
	{
		this(hostname, 22);
	}

	/**
	 * Prepares a fresh <code>Connection</code> object which can then be used
	 * to establish a connection to the specified SSH-2 server.
	 * 
	 * @param hostname
	 *            the host where we later want to connect to.
	 * @param port
	 *            port on the server, normally 22.
	 */
	public Connection(String hostname, int port)
	{
		this.hostname = hostname;
		this.port = port;
	}

	/**
	 * After a successful connect, one has to authenticate oneself. This method
	 * is based on DSA (it uses DSA to sign a challenge sent by the server).
	 * <p>
	 * If the authentication phase is complete, <code>true</code> will be
	 * returned. If the server does not accept the request (or if further
	 * authentication steps are needed), <code>false</code> is returned and
	 * one can retry either by using this or any other authentication method
	 * (use the <code>getRemainingAuthMethods</code> method to get a list of
	 * the remaining possible methods).
	 * 
	 * @param user
	 *            A <code>String</code> holding the username.
	 * @param pem
	 *            A <code>String</code> containing the DSA private key of the
	 *            user in OpenSSH key format (PEM, you can't miss the
	 *            "-----BEGIN DSA PRIVATE KEY-----" tag). The string may contain
	 *            linefeeds.
	 * @param password
	 *            If the PEM string is 3DES encrypted ("DES-EDE3-CBC"), then you
	 *            must specify the password. Otherwise, this argument will be
	 *            ignored and can be set to <code>null</code>.
	 * 
	 * @return whether the connection is now authenticated.
	 * @throws IOException
	 * 
	 * @deprecated You should use one of the
	 *             {@link #authenticateWithPublicKey(String, File, String) authenticateWithPublicKey()}
	 *             methods, this method is just a wrapper for it and will
	 *             disappear in future builds.
	 * 
	 */
	public synchronized boolean authenticateWithDSA(String user, String pem, String password) throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Connection is not established!");

		if (authenticated)
			throw new IllegalStateException("Connection is already authenticated!");

		if (am == null)
			am = new AuthenticationManager(tm);

		if (cm == null)
			cm = new ChannelManager(tm);

		if (user == null)
			throw new IllegalArgumentException("user argument is null");

		if (pem == null)
			throw new IllegalArgumentException("pem argument is null");

		authenticated = am.authenticatePublicKey(user, pem.toCharArray(), password, getOrCreateSecureRND());

		return authenticated;
	}

	/**
	 * A wrapper that calls
	 * {@link #authenticateWithKeyboardInteractive(String, String[], InteractiveCallback)
	 * authenticateWithKeyboardInteractivewith} a <code>null</code> submethod
	 * list.
	 * 
	 * @param user
	 *            A <code>String</code> holding the username.
	 * @param cb
	 *            An <code>InteractiveCallback</code> which will be used to
	 *            determine the responses to the questions asked by the server.
	 * @return whether the connection is now authenticated.
	 * @throws IOException
	 */
	public synchronized boolean authenticateWithKeyboardInteractive(String user, InteractiveCallback cb)
			throws IOException
	{
		return authenticateWithKeyboardInteractive(user, null, cb);
	}

	/**
	 * After a successful connect, one has to authenticate oneself. This method
	 * is based on "keyboard-interactive", specified in
	 * draft-ietf-secsh-auth-kbdinteract-XX. Basically, you have to define a
	 * callback object which will be feeded with challenges generated by the
	 * server. Answers are then sent back to the server. It is possible that the
	 * callback will be called several times during the invocation of this
	 * method (e.g., if the server replies to the callback's answer(s) with
	 * another challenge...)
	 * <p>
	 * If the authentication phase is complete, <code>true</code> will be
	 * returned. If the server does not accept the request (or if further
	 * authentication steps are needed), <code>false</code> is returned and
	 * one can retry either by using this or any other authentication method
	 * (use the <code>getRemainingAuthMethods</code> method to get a list of
	 * the remaining possible methods).
	 * <p>
	 * Note: some SSH servers advertise "keyboard-interactive", however, any
	 * interactive request will be denied (without having sent any challenge to
	 * the client).
	 * 
	 * @param user
	 *            A <code>String</code> holding the username.
	 * @param submethods
	 *            An array of submethod names, see
	 *            draft-ietf-secsh-auth-kbdinteract-XX. May be <code>null</code>
	 *            to indicate an empty list.
	 * @param cb
	 *            An <code>InteractiveCallback</code> which will be used to
	 *            determine the responses to the questions asked by the server.
	 * 
	 * @return whether the connection is now authenticated.
	 * @throws IOException
	 */
	public synchronized boolean authenticateWithKeyboardInteractive(String user, String[] submethods,
			InteractiveCallback cb) throws IOException
	{
		if (cb == null)
			throw new IllegalArgumentException("Callback may not ne NULL!");

		if (tm == null)
			throw new IllegalStateException("Connection is not established!");

		if (authenticated)
			throw new IllegalStateException("Connection is already authenticated!");

		if (am == null)
			am = new AuthenticationManager(tm);

		if (cm == null)
			cm = new ChannelManager(tm);

		if (user == null)
			throw new IllegalArgumentException("user argument is null");

		authenticated = am.authenticateInteractive(user, submethods, cb);

		return authenticated;
	}

	/**
	 * After a successful connect, one has to authenticate oneself. This method
	 * sends username and password to the server.
	 * <p>
	 * If the authentication phase is complete, <code>true</code> will be
	 * returned. If the server does not accept the request (or if further
	 * authentication steps are needed), <code>false</code> is returned and
	 * one can retry either by using this or any other authentication method
	 * (use the <code>getRemainingAuthMethods</code> method to get a list of
	 * the remaining possible methods).
	 * <p>
	 * Note: if this method fails, then please double-check that it is actually
	 * offered by the server (use
	 * {@link #getRemainingAuthMethods(String) getRemainingAuthMethods()}.
	 * <p>
	 * Often, password authentication is disabled, but users are not aware of
	 * it. Many servers only offer "publickey" and "keyboard-interactive".
	 * However, even though "keyboard-interactive" *feels* like password
	 * authentication (e.g., when using the putty or openssh clients) it is
	 * *not* the same mechanism.
	 * 
	 * @param user
	 * @param password
	 * @return if the connection is now authenticated.
	 * @throws IOException
	 */
	public synchronized boolean authenticateWithPassword(String user, String password) throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Connection is not established!");

		if (authenticated)
			throw new IllegalStateException("Connection is already authenticated!");

		if (am == null)
			am = new AuthenticationManager(tm);

		if (cm == null)
			cm = new ChannelManager(tm);

		if (user == null)
			throw new IllegalArgumentException("user argument is null");

		if (password == null)
			throw new IllegalArgumentException("password argument is null");

		authenticated = am.authenticatePassword(user, password);

		return authenticated;
	}

	/**
	 * After a successful connect, one has to authenticate oneself. This method
	 * can be used to explicitly use the special "none" authentication method
	 * (where only a username has to be specified).
	 * <p>
	 * Note 1: The "none" method may always be tried by clients, however as by
	 * the specs, the server will not explicitly announce it. In other words,
	 * the "none" token will never show up in the list returned by
	 * {@link #getRemainingAuthMethods(String)}.
	 * <p>
	 * Note 2: no matter which one of the authenticateWithXXX() methods you
	 * call, the library will always issue exactly one initial "none"
	 * authentication request to retrieve the initially allowed list of
	 * authentication methods by the server. Please read RFC 4252 for the
	 * details.
	 * <p>
	 * If the authentication phase is complete, <code>true</code> will be
	 * returned. If further authentication steps are needed, <code>false</code>
	 * is returned and one can retry by any other authentication method (use the
	 * <code>getRemainingAuthMethods</code> method to get a list of the
	 * remaining possible methods).
	 * 
	 * @param user
	 * @return if the connection is now authenticated.
	 * @throws IOException
	 */
	public synchronized boolean authenticateWithNone(String user) throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Connection is not established!");

		if (authenticated)
			throw new IllegalStateException("Connection is already authenticated!");

		if (am == null)
			am = new AuthenticationManager(tm);

		if (cm == null)
			cm = new ChannelManager(tm);

		if (user == null)
			throw new IllegalArgumentException("user argument is null");

		/* Trigger the sending of the PacketUserauthRequestNone packet */
		/* (if not already done) */

		authenticated = am.authenticateNone(user);

		return authenticated;
	}

	/**
	 * After a successful connect, one has to authenticate oneself. The
	 * authentication method "publickey" works by signing a challenge sent by
	 * the server. The signature is either DSA or RSA based - it just depends on
	 * the type of private key you specify, either a DSA or RSA private key in
	 * PEM format. And yes, this is may seem to be a little confusing, the
	 * method is called "publickey" in the SSH-2 protocol specification, however
	 * since we need to generate a signature, you actually have to supply a
	 * private key =).
	 * <p>
	 * The private key contained in the PEM file may also be encrypted
	 * ("Proc-Type: 4,ENCRYPTED"). The library supports DES-CBC and DES-EDE3-CBC
	 * encryption, as well as the more exotic PEM encrpytions AES-128-CBC,
	 * AES-192-CBC and AES-256-CBC.
	 * <p>
	 * If the authentication phase is complete, <code>true</code> will be
	 * returned. If the server does not accept the request (or if further
	 * authentication steps are needed), <code>false</code> is returned and
	 * one can retry either by using this or any other authentication method
	 * (use the <code>getRemainingAuthMethods</code> method to get a list of
	 * the remaining possible methods).
	 * <p>
	 * NOTE PUTTY USERS: Event though your key file may start with
	 * "-----BEGIN..." it is not in the expected format. You have to convert it
	 * to the OpenSSH key format by using the "puttygen" tool (can be downloaded
	 * from the Putty website). Simply load your key and then use the
	 * "Conversions/Export OpenSSH key" functionality to get a proper PEM file.
	 * 
	 * @param user
	 *            A <code>String</code> holding the username.
	 * @param pemPrivateKey
	 *            A <code>char[]</code> containing a DSA or RSA private key of
	 *            the user in OpenSSH key format (PEM, you can't miss the
	 *            "-----BEGIN DSA PRIVATE KEY-----" or "-----BEGIN RSA PRIVATE
	 *            KEY-----" tag). The char array may contain
	 *            linebreaks/linefeeds.
	 * @param password
	 *            If the PEM structure is encrypted ("Proc-Type: 4,ENCRYPTED")
	 *            then you must specify a password. Otherwise, this argument
	 *            will be ignored and can be set to <code>null</code>.
	 * 
	 * @return whether the connection is now authenticated.
	 * @throws IOException
	 */
	public synchronized boolean authenticateWithPublicKey(String user, char[] pemPrivateKey, String password)
			throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Connection is not established!");

		if (authenticated)
			throw new IllegalStateException("Connection is already authenticated!");

		if (am == null)
			am = new AuthenticationManager(tm);

		if (cm == null)
			cm = new ChannelManager(tm);

		if (user == null)
			throw new IllegalArgumentException("user argument is null");

		if (pemPrivateKey == null)
			throw new IllegalArgumentException("pemPrivateKey argument is null");

		authenticated = am.authenticatePublicKey(user, pemPrivateKey, password, getOrCreateSecureRND());

		return authenticated;
	}

	/**
	 * A convenience wrapper function which reads in a private key (PEM format,
	 * either DSA or RSA) and then calls
	 * <code>authenticateWithPublicKey(String, char[], String)</code>.
	 * <p>
	 * NOTE PUTTY USERS: Event though your key file may start with
	 * "-----BEGIN..." it is not in the expected format. You have to convert it
	 * to the OpenSSH key format by using the "puttygen" tool (can be downloaded
	 * from the Putty website). Simply load your key and then use the
	 * "Conversions/Export OpenSSH key" functionality to get a proper PEM file.
	 * 
	 * @param user
	 *            A <code>String</code> holding the username.
	 * @param pemFile
	 *            A <code>File</code> object pointing to a file containing a
	 *            DSA or RSA private key of the user in OpenSSH key format (PEM,
	 *            you can't miss the "-----BEGIN DSA PRIVATE KEY-----" or
	 *            "-----BEGIN RSA PRIVATE KEY-----" tag).
	 * @param password
	 *            If the PEM file is encrypted then you must specify the
	 *            password. Otherwise, this argument will be ignored and can be
	 *            set to <code>null</code>.
	 * 
	 * @return whether the connection is now authenticated.
	 * @throws IOException
	 */
	public synchronized boolean authenticateWithPublicKey(String user, File pemFile, String password)
			throws IOException
	{
		if (pemFile == null)
			throw new IllegalArgumentException("pemFile argument is null");

		char[] buff = new char[256];

		CharArrayWriter cw = new CharArrayWriter();

		FileReader fr = new FileReader(pemFile);

		while (true)
		{
			int len = fr.read(buff);
			if (len < 0)
				break;
			cw.write(buff, 0, len);
		}

		fr.close();

		return authenticateWithPublicKey(user, cw.toCharArray(), password);
	}

	/**
	 * Add a {@link ConnectionMonitor} to this connection. Can be invoked at any
	 * time, but it is best to add connection monitors before invoking
	 * <code>connect()</code> to avoid glitches (e.g., you add a connection
	 * monitor after a successful connect(), but the connection has died in the
	 * mean time. Then, your connection monitor won't be notified.)
	 * <p>
	 * You can add as many monitors as you like.
	 * 
	 * @see ConnectionMonitor
	 * 
	 * @param cmon
	 *            An object implementing the <code>ConnectionMonitor</code>
	 *            interface.
	 */
	public synchronized void addConnectionMonitor(ConnectionMonitor cmon)
	{
		if (cmon == null)
			throw new IllegalArgumentException("cmon argument is null");

		connectionMonitors.addElement(cmon);

		if (tm != null)
			tm.setConnectionMonitors(connectionMonitors);
	}

	/**
	 * Close the connection to the SSH-2 server. All assigned sessions will be
	 * closed, too. Can be called at any time. Don't forget to call this once
	 * you don't need a connection anymore - otherwise the receiver thread may
	 * run forever.
	 */
	public synchronized void close()
	{
		Throwable t = new Throwable("Closed due to user request.");
		close(t, false);
	}

	private void close(Throwable t, boolean hard)
	{
		if (cm != null)
			cm.closeAllChannels();

		if (tm != null)
		{
			tm.close(t, hard == false);
			tm = null;
		}
		am = null;
		cm = null;
		authenticated = false;
	}

	/**
	 * Same as
	 * {@link #connect(ServerHostKeyVerifier, int, int) connect(null, 0, 0)}.
	 * 
	 * @return see comments for the
	 *         {@link #connect(ServerHostKeyVerifier, int, int) connect(ServerHostKeyVerifier, int, int)}
	 *         method.
	 * @throws IOException
	 */
	public synchronized ConnectionInfo connect() throws IOException
	{
		return connect(null, 0, 0);
	}

	/**
	 * Same as
	 * {@link #connect(ServerHostKeyVerifier, int, int) connect(verifier, 0, 0)}.
	 * 
	 * @return see comments for the
	 *         {@link #connect(ServerHostKeyVerifier, int, int) connect(ServerHostKeyVerifier, int, int)}
	 *         method.
	 * @throws IOException
	 */
	public synchronized ConnectionInfo connect(ServerHostKeyVerifier verifier) throws IOException
	{
		return connect(verifier, 0, 0);
	}

	/**
	 * Connect to the SSH-2 server and, as soon as the server has presented its
	 * host key, use the
	 * {@link ServerHostKeyVerifier#verifyServerHostKey(String, int, String,
	 * byte[]) ServerHostKeyVerifier.verifyServerHostKey()} method of the
	 * <code>verifier</code> to ask for permission to proceed. If
	 * <code>verifier</code> is <code>null</code>, then any host key will
	 * be accepted - this is NOT recommended, since it makes man-in-the-middle
	 * attackes VERY easy (somebody could put a proxy SSH server between you and
	 * the real server).
	 * <p>
	 * Note: The verifier will be called before doing any crypto calculations
	 * (i.e., diffie-hellman). Therefore, if you don't like the presented host
	 * key then no CPU cycles are wasted (and the evil server has less
	 * information about us).
	 * <p>
	 * However, it is still possible that the server presented a fake host key:
	 * the server cheated (typically a sign for a man-in-the-middle attack) and
	 * is not able to generate a signature that matches its host key. Don't
	 * worry, the library will detect such a scenario later when checking the
	 * signature (the signature cannot be checked before having completed the
	 * diffie-hellman exchange).
	 * <p>
	 * Note 2: The {@link ServerHostKeyVerifier#verifyServerHostKey(String, int,
	 * String, byte[]) ServerHostKeyVerifier.verifyServerHostKey()} method will
	 * *NOT* be called from the current thread, the call is being made from a
	 * background thread (there is a background dispatcher thread for every
	 * established connection).
	 * <p>
	 * Note 3: This method will block as long as the key exchange of the
	 * underlying connection has not been completed (and you have not specified
	 * any timeouts).
	 * <p>
	 * Note 4: If you want to re-use a connection object that was successfully
	 * connected, then you must call the {@link #close()} method before invoking
	 * <code>connect()</code> again.
	 * 
	 * @param verifier
	 *            An object that implements the {@link ServerHostKeyVerifier}
	 *            interface. Pass <code>null</code> to accept any server host
	 *            key - NOT recommended.
	 * 
	 * @param connectTimeout
	 *            Connect the underlying TCP socket to the server with the given
	 *            timeout value (non-negative, in milliseconds). Zero means no
	 *            timeout. If a proxy is being used (see
	 *            {@link #setProxyData(ProxyData)}), then this timeout is used
	 *            for the connection establishment to the proxy.
	 * 
	 * @param kexTimeout
	 *            Timeout for complete connection establishment (non-negative,
	 *            in milliseconds). Zero means no timeout. The timeout counts
	 *            from the moment you invoke the connect() method and is
	 *            cancelled as soon as the first key-exchange round has
	 *            finished. It is possible that the timeout event will be fired
	 *            during the invocation of the <code>verifier</code> callback,
	 *            but it will only have an effect after the
	 *            <code>verifier</code> returns.
	 * 
	 * @return A {@link ConnectionInfo} object containing the details of the
	 *         established connection.
	 * 
	 * @throws IOException
	 *             If any problem occurs, e.g., the server's host key is not
	 *             accepted by the <code>verifier</code> or there is problem
	 *             during the initial crypto setup (e.g., the signature sent by
	 *             the server is wrong).
	 *             <p>
	 *             In case of a timeout (either connectTimeout or kexTimeout) a
	 *             SocketTimeoutException is thrown.
	 *             <p>
	 *             An exception may also be thrown if the connection was already
	 *             successfully connected (no matter if the connection broke in
	 *             the mean time) and you invoke <code>connect()</code> again
	 *             without having called {@link #close()} first.
	 *             <p>
	 *             If a HTTP proxy is being used and the proxy refuses the
	 *             connection, then a {@link HTTPProxyException} may be thrown,
	 *             which contains the details returned by the proxy. If the
	 *             proxy is buggy and does not return a proper HTTP response,
	 *             then a normal IOException is thrown instead.
	 */
	public synchronized ConnectionInfo connect(ServerHostKeyVerifier verifier, int connectTimeout, int kexTimeout)
			throws IOException
	{
		final class TimeoutState
		{
			boolean isCancelled = false;
			boolean timeoutSocketClosed = false;
		}

		if (tm != null)
			throw new IOException("Connection to " + hostname + " is already in connected state!");

		if (connectTimeout < 0)
			throw new IllegalArgumentException("connectTimeout must be non-negative!");

		if (kexTimeout < 0)
			throw new IllegalArgumentException("kexTimeout must be non-negative!");

		final TimeoutState state = new TimeoutState();

		tm = new TransportManager(hostname, port);

		tm.setConnectionMonitors(connectionMonitors);

		/*
		 * Make sure that the runnable below will observe the new value of "tm"
		 * and "state" (the runnable will be executed in a different thread,
		 * which may be already running, that is why we need a memory barrier
		 * here). See also the comment in Channel.java if you are interested in
		 * the details.
		 * 
		 * OKOK, this is paranoid since adding the runnable to the todo list of
		 * the TimeoutService will ensure that all writes have been flushed
		 * before the Runnable reads anything (there is a synchronized block in
		 * TimeoutService.addTimeoutHandler).
		 */

		synchronized (tm)
		{
			/* We could actually synchronize on anything. */
		}

		try
		{
			TimeoutToken token = null;

			if (kexTimeout > 0)
			{
				final Runnable timeoutHandler = new Runnable()
				{
					public void run()
					{
						synchronized (state)
						{
							if (state.isCancelled)
								return;
							state.timeoutSocketClosed = true;
							tm.close(new SocketTimeoutException("The connect timeout expired"), false);
						}
					}
				};

				long timeoutHorizont = System.currentTimeMillis() + kexTimeout;

				token = TimeoutService.addTimeoutHandler(timeoutHorizont, timeoutHandler);
			}

			try
			{
				tm.initialize(cryptoWishList, verifier, dhgexpara, connectTimeout, getOrCreateSecureRND(), proxyData);
			}
			catch (SocketTimeoutException se)
			{
				throw (SocketTimeoutException) new SocketTimeoutException(
						"The connect() operation on the socket timed out.").initCause(se);
			}

			tm.setTcpNoDelay(tcpNoDelay);

			/* Wait until first KEX has finished */

			ConnectionInfo ci = tm.getConnectionInfo(1);

			/* Now try to cancel the timeout, if needed */

			if (token != null)
			{
				TimeoutService.cancelTimeoutHandler(token);

				/* Were we too late? */

				synchronized (state)
				{
					if (state.timeoutSocketClosed)
						throw new IOException("This exception will be replaced by the one below =)");
					/*
					 * Just in case the "cancelTimeoutHandler" invocation came
					 * just a little bit too late but the handler did not enter
					 * the semaphore yet - we can still stop it.
					 */
					state.isCancelled = true;
				}
			}

			return ci;
		}
		catch (SocketTimeoutException ste)
		{
			throw ste;
		}
		catch (IOException e1)
		{
			/* This will also invoke any registered connection monitors */
			close(new Throwable("There was a problem during connect."), false);

			synchronized (state)
			{
				/*
				 * Show a clean exception, not something like "the socket is
				 * closed!?!"
				 */
				if (state.timeoutSocketClosed)
					throw new SocketTimeoutException("The kexTimeout (" + kexTimeout + " ms) expired.");
			}

			/* Do not wrap a HTTPProxyException */
			if (e1 instanceof HTTPProxyException)
				throw e1;

			throw (IOException) new IOException("There was a problem while connecting to " + hostname + ":" + port)
					.initCause(e1);
		}
	}

	/**
	 * Creates a new {@link LocalPortForwarder}. A
	 * <code>LocalPortForwarder</code> forwards TCP/IP connections that arrive
	 * at a local port via the secure tunnel to another host (which may or may
	 * not be identical to the remote SSH-2 server).
	 * <p>
	 * This method must only be called after one has passed successfully the
	 * authentication step. There is no limit on the number of concurrent
	 * forwardings.
	 * 
	 * @param local_port
	 *            the local port the LocalPortForwarder shall bind to.
	 * @param host_to_connect
	 *            target address (IP or hostname)
	 * @param port_to_connect
	 *            target port
	 * @return A {@link LocalPortForwarder} object.
	 * @throws IOException
	 */
	public synchronized LocalPortForwarder createLocalPortForwarder(int local_port, String host_to_connect,
			int port_to_connect) throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Cannot forward ports, you need to establish a connection first.");

		if (!authenticated)
			throw new IllegalStateException("Cannot forward ports, connection is not authenticated.");

		return new LocalPortForwarder(cm, local_port, host_to_connect, port_to_connect);
	}

	/**
	 * Creates a new {@link LocalPortForwarder}. A
	 * <code>LocalPortForwarder</code> forwards TCP/IP connections that arrive
	 * at a local port via the secure tunnel to another host (which may or may
	 * not be identical to the remote SSH-2 server).
	 * <p>
	 * This method must only be called after one has passed successfully the
	 * authentication step. There is no limit on the number of concurrent
	 * forwardings.
	 * 
	 * @param addr
	 *            specifies the InetSocketAddress where the local socket shall
	 *            be bound to.
	 * @param host_to_connect
	 *            target address (IP or hostname)
	 * @param port_to_connect
	 *            target port
	 * @return A {@link LocalPortForwarder} object.
	 * @throws IOException
	 */
	public synchronized LocalPortForwarder createLocalPortForwarder(InetSocketAddress addr, String host_to_connect,
			int port_to_connect) throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Cannot forward ports, you need to establish a connection first.");

		if (!authenticated)
			throw new IllegalStateException("Cannot forward ports, connection is not authenticated.");

		return new LocalPortForwarder(cm, addr, host_to_connect, port_to_connect);
	}

	/**
	 * Creates a new {@link LocalStreamForwarder}. A
	 * <code>LocalStreamForwarder</code> manages an Input/Outputstream pair
	 * that is being forwarded via the secure tunnel into a TCP/IP connection to
	 * another host (which may or may not be identical to the remote SSH-2
	 * server).
	 * 
	 * @param host_to_connect
	 * @param port_to_connect
	 * @return A {@link LocalStreamForwarder} object.
	 * @throws IOException
	 */
	public synchronized LocalStreamForwarder createLocalStreamForwarder(String host_to_connect, int port_to_connect)
			throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Cannot forward, you need to establish a connection first.");

		if (!authenticated)
			throw new IllegalStateException("Cannot forward, connection is not authenticated.");

		return new LocalStreamForwarder(cm, host_to_connect, port_to_connect);
	}

	/**
	 * Create a very basic {@link SCPClient} that can be used to copy files
	 * from/to the SSH-2 server.
	 * <p>
	 * Works only after one has passed successfully the authentication step.
	 * There is no limit on the number of concurrent SCP clients.
	 * <p>
	 * Note: This factory method will probably disappear in the future.
	 * 
	 * @return A {@link SCPClient} object.
	 * @throws IOException
	 */
	public synchronized SCPClient createSCPClient() throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Cannot create SCP client, you need to establish a connection first.");

		if (!authenticated)
			throw new IllegalStateException("Cannot create SCP client, connection is not authenticated.");

		return new SCPClient(this);
	}

	/**
	 * Force an asynchronous key re-exchange (the call does not block). The
	 * latest values set for MAC, Cipher and DH group exchange parameters will
	 * be used. If a key exchange is currently in progress, then this method has
	 * the only effect that the so far specified parameters will be used for the
	 * next (server driven) key exchange.
	 * <p>
	 * Note: This implementation will never start a key exchange (other than the
	 * initial one) unless you or the SSH-2 server ask for it.
	 * 
	 * @throws IOException
	 *             In case of any failure behind the scenes.
	 */
	public synchronized void forceKeyExchange() throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("You need to establish a connection first.");

		tm.forceKeyExchange(cryptoWishList, dhgexpara);
	}

	/**
	 * Returns the hostname that was passed to the constructor.
	 * 
	 * @return the hostname
	 */
	public synchronized String getHostname()
	{
		return hostname;
	}

	/**
	 * Returns the port that was passed to the constructor.
	 * 
	 * @return the TCP port
	 */
	public synchronized int getPort()
	{
		return port;
	}

	/**
	 * Returns a {@link ConnectionInfo} object containing the details of the
	 * connection. Can be called as soon as the connection has been established
	 * (successfully connected).
	 * 
	 * @return A {@link ConnectionInfo} object.
	 * @throws IOException
	 *             In case of any failure behind the scenes.
	 */
	public synchronized ConnectionInfo getConnectionInfo() throws IOException
	{
		if (tm == null)
			throw new IllegalStateException(
					"Cannot get details of connection, you need to establish a connection first.");
		return tm.getConnectionInfo(1);
	}

	/**
	 * After a successful connect, one has to authenticate oneself. This method
	 * can be used to tell which authentication methods are supported by the
	 * server at a certain stage of the authentication process (for the given
	 * username).
	 * <p>
	 * Note 1: the username will only be used if no authentication step was done
	 * so far (it will be used to ask the server for a list of possible
	 * authentication methods by sending the initial "none" request). Otherwise,
	 * this method ignores the user name and returns a cached method list (which
	 * is based on the information contained in the last negative server
	 * response).
	 * <p>
	 * Note 2: the server may return method names that are not supported by this
	 * implementation.
	 * <p>
	 * After a successful authentication, this method must not be called
	 * anymore.
	 * 
	 * @param user
	 *            A <code>String</code> holding the username.
	 * 
	 * @return a (possibly emtpy) array holding authentication method names.
	 * @throws IOException
	 */
	public synchronized String[] getRemainingAuthMethods(String user) throws IOException
	{
		if (user == null)
			throw new IllegalArgumentException("user argument may not be NULL!");

		if (tm == null)
			throw new IllegalStateException("Connection is not established!");

		if (authenticated)
			throw new IllegalStateException("Connection is already authenticated!");

		if (am == null)
			am = new AuthenticationManager(tm);

		if (cm == null)
			cm = new ChannelManager(tm);

		return am.getRemainingMethods(user);
	}

	/**
	 * Determines if the authentication phase is complete. Can be called at any
	 * time.
	 * 
	 * @return <code>true</code> if no further authentication steps are
	 *         needed.
	 */
	public synchronized boolean isAuthenticationComplete()
	{
		return authenticated;
	}

	/**
	 * Returns true if there was at least one failed authentication request and
	 * the last failed authentication request was marked with "partial success"
	 * by the server. This is only needed in the rare case of SSH-2 server
	 * setups that cannot be satisfied with a single successful authentication
	 * request (i.e., multiple authentication steps are needed.)
	 * <p>
	 * If you are interested in the details, then have a look at RFC4252.
	 * 
	 * @return if the there was a failed authentication step and the last one
	 *         was marked as a "partial success".
	 */
	public synchronized boolean isAuthenticationPartialSuccess()
	{
		if (am == null)
			return false;

		return am.getPartialSuccess();
	}

	/**
	 * Checks if a specified authentication method is available. This method is
	 * actually just a wrapper for {@link #getRemainingAuthMethods(String)
	 * getRemainingAuthMethods()}.
	 * 
	 * @param user
	 *            A <code>String</code> holding the username.
	 * @param method
	 *            An authentication method name (e.g., "publickey", "password",
	 *            "keyboard-interactive") as specified by the SSH-2 standard.
	 * @return if the specified authentication method is currently available.
	 * @throws IOException
	 */
	public synchronized boolean isAuthMethodAvailable(String user, String method) throws IOException
	{
		if (method == null)
			throw new IllegalArgumentException("method argument may not be NULL!");

		String methods[] = getRemainingAuthMethods(user);

		for (int i = 0; i < methods.length; i++)
		{
			if (methods[i].compareTo(method) == 0)
				return true;
		}

		return false;
	}

	private final SecureRandom getOrCreateSecureRND()
	{
		if (generator == null)
			generator = new SecureRandom();

		return generator;
	}

	/**
	 * Open a new {@link Session} on this connection. Works only after one has
	 * passed successfully the authentication step. There is no limit on the
	 * number of concurrent sessions.
	 * 
	 * @return A {@link Session} object.
	 * @throws IOException
	 */
	public synchronized Session openSession() throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("Cannot open session, you need to establish a connection first.");

		if (!authenticated)
			throw new IllegalStateException("Cannot open session, connection is not authenticated.");

		return new Session(cm, getOrCreateSecureRND());
	}

	/**
	 * Send an SSH_MSG_IGNORE packet. This method will generate a random data
	 * attribute (length between 0 (invlusive) and 16 (exclusive) bytes,
	 * contents are random bytes).
	 * <p>
	 * This method must only be called once the connection is established.
	 * 
	 * @throws IOException
	 */
	public synchronized void sendIgnorePacket() throws IOException
	{
		SecureRandom rnd = getOrCreateSecureRND();

		byte[] data = new byte[rnd.nextInt(16)];
		rnd.nextBytes(data);

		sendIgnorePacket(data);
	}

	/**
	 * Send an SSH_MSG_IGNORE packet with the given data attribute.
	 * <p>
	 * This method must only be called once the connection is established.
	 * 
	 * @throws IOException
	 */
	public synchronized void sendIgnorePacket(byte[] data) throws IOException
	{
		if (data == null)
			throw new IllegalArgumentException("data argument must not be null.");

		if (tm == null)
			throw new IllegalStateException(
					"Cannot send SSH_MSG_IGNORE packet, you need to establish a connection first.");

		PacketIgnore pi = new PacketIgnore();
		pi.setData(data);

		tm.sendMessage(pi.getPayload());
	}

	/**
	 * Removes duplicates from a String array, keeps only first occurence of
	 * each element. Does not destroy order of elements; can handle nulls. Uses
	 * a very efficient O(N^2) algorithm =)
	 * 
	 * @param list
	 *            a String array.
	 * @return a cleaned String array.
	 */
	private String[] removeDuplicates(String[] list)
	{
		if ((list == null) || (list.length < 2))
			return list;

		String[] list2 = new String[list.length];

		int count = 0;

		for (int i = 0; i < list.length; i++)
		{
			boolean duplicate = false;

			String element = list[i];

			for (int j = 0; j < count; j++)
			{
				if (((element == null) && (list2[j] == null)) || ((element != null) && (element.equals(list2[j]))))
				{
					duplicate = true;
					break;
				}
			}

			if (duplicate)
				continue;

			list2[count++] = list[i];
		}

		if (count == list2.length)
			return list2;

		String[] tmp = new String[count];
		System.arraycopy(list2, 0, tmp, 0, count);

		return tmp;
	}

	/**
	 * Unless you know what you are doing, you will never need this.
	 * 
	 * @param ciphers
	 */
	public synchronized void setClient2ServerCiphers(String[] ciphers)
	{
		if ((ciphers == null) || (ciphers.length == 0))
			throw new IllegalArgumentException();
		ciphers = removeDuplicates(ciphers);
		BlockCipherFactory.checkCipherList(ciphers);
		cryptoWishList.c2s_enc_algos = ciphers;
	}

	/**
	 * Unless you know what you are doing, you will never need this.
	 * 
	 * @param macs
	 */
	public synchronized void setClient2ServerMACs(String[] macs)
	{
		if ((macs == null) || (macs.length == 0))
			throw new IllegalArgumentException();
		macs = removeDuplicates(macs);
		MAC.checkMacList(macs);
		cryptoWishList.c2s_mac_algos = macs;
	}

	/**
	 * Sets the parameters for the diffie-hellman group exchange. Unless you
	 * know what you are doing, you will never need this. Default values are
	 * defined in the {@link DHGexParameters} class.
	 * 
	 * @param dgp
	 *            {@link DHGexParameters}, non null.
	 * 
	 */
	public synchronized void setDHGexParameters(DHGexParameters dgp)
	{
		if (dgp == null)
			throw new IllegalArgumentException();

		dhgexpara = dgp;
	}

	/**
	 * Unless you know what you are doing, you will never need this.
	 * 
	 * @param ciphers
	 */
	public synchronized void setServer2ClientCiphers(String[] ciphers)
	{
		if ((ciphers == null) || (ciphers.length == 0))
			throw new IllegalArgumentException();
		ciphers = removeDuplicates(ciphers);
		BlockCipherFactory.checkCipherList(ciphers);
		cryptoWishList.s2c_enc_algos = ciphers;
	}

	/**
	 * Unless you know what you are doing, you will never need this.
	 * 
	 * @param macs
	 */
	public synchronized void setServer2ClientMACs(String[] macs)
	{
		if ((macs == null) || (macs.length == 0))
			throw new IllegalArgumentException();

		macs = removeDuplicates(macs);
		MAC.checkMacList(macs);
		cryptoWishList.s2c_mac_algos = macs;
	}

	/**
	 * Define the set of allowed server host key algorithms to be used for the
	 * following key exchange operations.
	 * <p>
	 * Unless you know what you are doing, you will never need this.
	 * 
	 * @param algos
	 *            An array of allowed server host key algorithms. SSH-2 defines
	 *            <code>ssh-dss</code> and <code>ssh-rsa</code>. The
	 *            entries of the array must be ordered after preference, i.e.,
	 *            the entry at index 0 is the most preferred one. You must
	 *            specify at least one entry.
	 */
	public synchronized void setServerHostKeyAlgorithms(String[] algos)
	{
		if ((algos == null) || (algos.length == 0))
			throw new IllegalArgumentException();

		algos = removeDuplicates(algos);
		KexManager.checkServerHostkeyAlgorithmsList(algos);
		cryptoWishList.serverHostKeyAlgorithms = algos;
	}

	/**
	 * Enable/disable TCP_NODELAY (disable/enable Nagle's algorithm) on the
	 * underlying socket.
	 * <p>
	 * Can be called at any time. If the connection has not yet been established
	 * then the passed value will be stored and set after the socket has been
	 * set up. The default value that will be used is <code>false</code>.
	 * 
	 * @param enable
	 *            the argument passed to the <code>Socket.setTCPNoDelay()</code>
	 *            method.
	 * @throws IOException
	 */
	public synchronized void setTCPNoDelay(boolean enable) throws IOException
	{
		tcpNoDelay = enable;

		if (tm != null)
			tm.setTcpNoDelay(enable);
	}

	/**
	 * Used to tell the library that the connection shall be established through
	 * a proxy server. It only makes sense to call this method before calling
	 * the {@link #connect() connect()} method.
	 * <p>
	 * At the moment, only HTTP proxies are supported.
	 * <p>
	 * Note: This method can be called any number of times. The
	 * {@link #connect() connect()} method will use the value set in the last
	 * preceding invocation of this method.
	 * 
	 * @see HTTPProxyData
	 * 
	 * @param proxyData
	 *            Connection information about the proxy. If <code>null</code>,
	 *            then no proxy will be used (non surprisingly, this is also the
	 *            default).
	 */
	public synchronized void setProxyData(ProxyData proxyData)
	{
		this.proxyData = proxyData;
	}

	/**
	 * Request a remote port forwarding. If successful, then forwarded
	 * connections will be redirected to the given target address. You can
	 * cancle a requested remote port forwarding by calling
	 * {@link #cancelRemotePortForwarding(int) cancelRemotePortForwarding()}.
	 * <p>
	 * A call of this method will block until the peer either agreed or
	 * disagreed to your request-
	 * <p>
	 * Note 1: this method typically fails if you
	 * <ul>
	 * <li>pass a port number for which the used remote user has not enough
	 * permissions (i.e., port &lt; 1024)</li>
	 * <li>or pass a port number that is already in use on the remote server</li>
	 * <li>or if remote port forwarding is disabled on the server.</li>
	 * </ul>
	 * <p>
	 * Note 2: (from the openssh man page): By default, the listening socket on
	 * the server will be bound to the loopback interface only. This may be
	 * overriden by specifying a bind address. Specifying a remote bind address
	 * will only succeed if the server's <b>GatewayPorts</b> option is enabled
	 * (see sshd_config(5)).
	 * 
	 * @param bindAddress
	 *            address to bind to on the server:
	 *            <ul>
	 *            <li>"" means that connections are to be accepted on all
	 *            protocol families supported by the SSH implementation</li>
	 *            <li>"0.0.0.0" means to listen on all IPv4 addresses</li>
	 *            <li>"::" means to listen on all IPv6 addresses</li>
	 *            <li>"localhost" means to listen on all protocol families
	 *            supported by the SSH implementation on loopback addresses
	 *            only, [RFC3330] and RFC3513]</li>
	 *            <li>"127.0.0.1" and "::1" indicate listening on the loopback
	 *            interfaces for IPv4 and IPv6 respectively</li>
	 *            </ul>
	 * @param bindPort
	 *            port number to bind on the server (must be &gt; 0)
	 * @param targetAddress
	 *            the target address (IP or hostname)
	 * @param targetPort
	 *            the target port
	 * @throws IOException
	 */
	public synchronized void requestRemotePortForwarding(String bindAddress, int bindPort, String targetAddress,
			int targetPort) throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("You need to establish a connection first.");

		if (!authenticated)
			throw new IllegalStateException("The connection is not authenticated.");

		if ((bindAddress == null) || (targetAddress == null) || (bindPort <= 0) || (targetPort <= 0))
			throw new IllegalArgumentException();

		cm.requestGlobalForward(bindAddress, bindPort, targetAddress, targetPort);
	}

	/**
	 * Cancel an earlier requested remote port forwarding. Currently active
	 * forwardings will not be affected (e.g., disrupted). Note that further
	 * connection forwarding requests may be received until this method has
	 * returned.
	 * 
	 * @param bindPort
	 *            the allocated port number on the server
	 * @throws IOException
	 *             if the remote side refuses the cancel request or another low
	 *             level error occurs (e.g., the underlying connection is
	 *             closed)
	 */
	public synchronized void cancelRemotePortForwarding(int bindPort) throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("You need to establish a connection first.");

		if (!authenticated)
			throw new IllegalStateException("The connection is not authenticated.");

		cm.requestCancelGlobalForward(bindPort);
	}

	/**
	 * Provide your own instance of SecureRandom. Can be used, e.g., if you want
	 * to seed the used SecureRandom generator manually.
	 * <p>
	 * The SecureRandom instance is used during key exchanges, public key
	 * authentication, x11 cookie generation and the like.
	 * 
	 * @param rnd
	 *            a SecureRandom instance
	 */
	public synchronized void setSecureRandom(SecureRandom rnd)
	{
		if (rnd == null)
			throw new IllegalArgumentException();

		this.generator = rnd;
	}

	/**
	 * Enable/disable debug logging. <b>Only do this when requested by Trilead
	 * support.</b>
	 * <p>
	 * For speed reasons, some static variables used to check whether debugging
	 * is enabled are not protected with locks. In other words, if you
	 * dynamicaly enable/disable debug logging, then some threads may still use
	 * the old setting. To be on the safe side, enable debugging before doing
	 * the <code>connect()</code> call.
	 * 
	 * @param enable
	 *            on/off
	 * @param logger
	 *            a {@link DebugLogger DebugLogger} instance, <code>null</code>
	 *            means logging using the simple logger which logs all messages
	 *            to to stderr. Ignored if enabled is <code>false</code>
	 */
	public synchronized void enableDebugging(boolean enable, DebugLogger logger)
	{
		Logger.enabled = enable;

		if (enable == false)
		{
			Logger.logger = null;
		}
		else
		{
			if (logger == null)
			{
				logger = new DebugLogger()
				{

					public void log(int level, String className, String message)
					{
						long now = System.currentTimeMillis();
						System.err.println(now + " : " + className + ": " + message);
					}
				};
			}
		}
	}

	/**
	 * This method can be used to perform end-to-end connection testing. It
	 * sends a 'ping' message to the server and waits for the 'pong' from the
	 * server.
	 * <p>
	 * When this method throws an exception, then you can assume that the
	 * connection should be abandoned.
	 * <p>
	 * Note: Works only after one has passed successfully the authentication
	 * step.
	 * <p>
	 * Implementation details: this method sends a SSH_MSG_GLOBAL_REQUEST
	 * request ('trilead-ping') to the server and waits for the
	 * SSH_MSG_REQUEST_FAILURE reply packet from the server.
	 * 
	 * @throws IOException
	 *             in case of any problem
	 */
	public synchronized void ping() throws IOException
	{
		if (tm == null)
			throw new IllegalStateException("You need to establish a connection first.");

		if (!authenticated)
			throw new IllegalStateException("The connection is not authenticated.");

		cm.requestGlobalTrileadPing();
	}

    public ClientServerHello getClientServerHello() {
        return tm == null ? null : tm.getClientServerHello();
    }
}
