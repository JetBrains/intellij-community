/*
 * Copyright (c) 2010-2018 Nathan Rajlich
 *
 *  Permission is hereby granted, free of charge, to any person
 *  obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without
 *  restriction, including without limitation the rights to use,
 *  copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following
 *  conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 *  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 *  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 *  OTHER DEALINGS IN THE SOFTWARE.
 */

package org.java_websocket.server;

import org.java_websocket.*;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <tt>WebSocketServer</tt> is an abstract class that only takes care of the
 * HTTP handshake portion of WebSockets. It's up to a subclass to add
 * functionality/purpose to the server.
 * 
 */
public abstract class WebSocketServer extends AbstractWebSocket implements Runnable {

	/**
	 * Logger instance
	 *
	 * @since 1.4.0
	 */
	private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

	private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

	/**
	 * Holds the list of active WebSocket connections. "Active" means WebSocket
	 * handshake is complete and socket can be written to, or read from.
	 */
	private final Collection<WebSocket> connections;
	/**
	 * The port number that this WebSocket server should listen on. Default is
	 * WebSocketImpl.DEFAULT_PORT.
	 */
	private final InetSocketAddress address;
	/**
	 * The socket channel for this WebSocket server.
	 */
	private ServerSocketChannel server;
	/**
	 * The 'Selector' used to get event keys from the underlying socket.
	 */
	private Selector selector;
	/**
	 * The Draft of the WebSocket protocol the Server is adhering to.
	 */
	private List<Draft> drafts;

	private Thread selectorthread;

	private final AtomicBoolean isclosed = new AtomicBoolean( false );

	protected List<WebSocketWorker> decoders;

	private List<WebSocketImpl> iqueue;
	private BlockingQueue<ByteBuffer> buffers;
	private int queueinvokes = 0;
	private final AtomicInteger queuesize = new AtomicInteger( 0 );

	private WebSocketServerFactory wsf = new DefaultWebSocketServerFactory();

	/**
	 * Creates a WebSocketServer that will attempt to
	 * listen on port <var>WebSocketImpl.DEFAULT_PORT</var>.
	 * 
	 * @see #WebSocketServer(InetSocketAddress, int, List, Collection) more details here
	 */
	public WebSocketServer()  {
		this(new InetSocketAddress(WebSocketImpl.DEFAULT_PORT ), AVAILABLE_PROCESSORS, null );
	}

	/**
	 * Creates a WebSocketServer that will attempt to bind/listen on the given <var>address</var>.
	 * 
	 * @see #WebSocketServer(InetSocketAddress, int, List, Collection) more details here
	 * @param address The address to listen to
	 */
	public WebSocketServer( InetSocketAddress address ) {
		this( address, AVAILABLE_PROCESSORS, null );
	}

	/**
	 * @see #WebSocketServer(InetSocketAddress, int, List, Collection) more details here
	 * @param address
	 *            The address (host:port) this server should listen on.
	 * @param decodercount
	 *            The number of {@link WebSocketWorker}s that will be used to process the incoming network data. By default this will be <code>Runtime.getRuntime().availableProcessors()</code>
	 */
	public WebSocketServer( InetSocketAddress address , int decodercount ) {
		this( address, decodercount, null );
	}

	/**
	 * @see #WebSocketServer(InetSocketAddress, int, List, Collection) more details here
	 *
	 * @param address
	 *            The address (host:port) this server should listen on.
	 * @param drafts
	 *            The versions of the WebSocket protocol that this server
	 *            instance should comply to. Clients that use an other protocol version will be rejected.
	 *
	 */
	public WebSocketServer( InetSocketAddress address , List<Draft> drafts ) {
		this( address, AVAILABLE_PROCESSORS, drafts );
	}

	/**
	 * @see #WebSocketServer(InetSocketAddress, int, List, Collection) more details here
	 *
	 * @param address
	 *            The address (host:port) this server should listen on.
	 * @param decodercount
	 *            The number of {@link WebSocketWorker}s that will be used to process the incoming network data. By default this will be <code>Runtime.getRuntime().availableProcessors()</code>
	 * @param drafts
	 *            The versions of the WebSocket protocol that this server
	 *            instance should comply to. Clients that use an other protocol version will be rejected.

	 */
	public WebSocketServer( InetSocketAddress address , int decodercount , List<Draft> drafts ) {
		this( address, decodercount, drafts, new HashSet<WebSocket>() );
	}

	/**
	 * Creates a WebSocketServer that will attempt to bind/listen on the given <var>address</var>,
	 * and comply with <tt>Draft</tt> version <var>draft</var>.
	 * 
	 * @param address
	 *            The address (host:port) this server should listen on.
	 * @param decodercount
	 *            The number of {@link WebSocketWorker}s that will be used to process the incoming network data. By default this will be <code>Runtime.getRuntime().availableProcessors()</code>
	 * @param drafts
	 *            The versions of the WebSocket protocol that this server
	 *            instance should comply to. Clients that use an other protocol version will be rejected.
	 * 
	 * @param connectionscontainer
	 *            Allows to specify a collection that will be used to store the websockets in. <br>
	 *            If you plan to often iterate through the currently connected websockets you may want to use a collection that does not require synchronization like a {@link CopyOnWriteArraySet}. In that case make sure that you overload {@link #removeConnection(WebSocket)} and {@link #addConnection(WebSocket)}.<br>
	 *            By default a {@link HashSet} will be used.
	 * 
	 * @see #removeConnection(WebSocket) for more control over syncronized operation
	 * @see <a href="https://github.com/TooTallNate/Java-WebSocket/wiki/Drafts" > more about drafts</a>
	 */
	public WebSocketServer( InetSocketAddress address , int decodercount , List<Draft> drafts , Collection<WebSocket> connectionscontainer ) {
		if( address == null || decodercount < 1 || connectionscontainer == null ) {
			throw new IllegalArgumentException( "address and connectionscontainer must not be null and you need at least 1 decoder" );
		}

		if( drafts == null )
			this.drafts = Collections.emptyList();
		else
			this.drafts = drafts;

		this.address = address;
		this.connections = connectionscontainer;
		setTcpNoDelay(false);
		setReuseAddr(false);
		iqueue = new LinkedList<WebSocketImpl>();

		decoders = new ArrayList<WebSocketWorker>( decodercount );
		buffers = new LinkedBlockingQueue<ByteBuffer>();
		for( int i = 0 ; i < decodercount ; i++ ) {
			WebSocketWorker ex = new WebSocketWorker();
			decoders.add( ex );
			ex.start();
		}
	}


	/**
	 * Starts the server selectorthread that binds to the currently set port number and
	 * listeners for WebSocket connection requests. Creates a fixed thread pool with the size {@link WebSocketServer#AVAILABLE_PROCESSORS}<br>
	 * May only be called once.
	 * 
	 * Alternatively you can call {@link WebSocketServer#run()} directly.
	 * 
	 * @throws IllegalStateException Starting an instance again
	 */
	public void start() {
		if( selectorthread != null )
			throw new IllegalStateException( getClass().getName() + " can only be started once." );
		new Thread( this ).start();
	}

	/**
	 * Closes all connected clients sockets, then closes the underlying
	 * ServerSocketChannel, effectively killing the server socket selectorthread,
	 * freeing the port the server was bound to and stops all internal workerthreads.
	 * 
	 * If this method is called before the server is started it will never start.
	 * 
	 * @param timeout
	 *            Specifies how many milliseconds the overall close handshaking may take altogether before the connections are closed without proper close handshaking.<br>
	 * 
	 * @throws InterruptedException Interrupt
	 */
	public void stop( int timeout ) throws InterruptedException {
		if( !isclosed.compareAndSet( false, true ) ) { // this also makes sure that no further connections will be added to this.connections
			return;
		}

		List<WebSocket> socketsToClose;

		// copy the connections in a list (prevent callback deadlocks)
		synchronized ( connections ) {
			socketsToClose = new ArrayList<WebSocket>( connections );
		}

		for( WebSocket ws : socketsToClose ) {
			ws.close( CloseFrame.GOING_AWAY );
		}

		wsf.close();

		synchronized ( this ) {
			if( selectorthread != null  && selector != null) {
				selector.wakeup();
				selectorthread.join( timeout );
			}
		}
	}
	public void stop() throws IOException , InterruptedException {
		stop( 0 );
	}

	/**
	 * Returns  all currently connected clients.
	 * This collection does not allow any modification e.g. removing a client.
	 *
	 * @return A unmodifiable collection of all currently connected clients
	 * @since 1.3.8
	 */
	public Collection<WebSocket> getConnections() {
		return Collections.unmodifiableCollection( new ArrayList<WebSocket>(connections) );
	}

	public InetSocketAddress getAddress() {
		return this.address;
	}

	/**
	 * Gets the port number that this server listens on.
	 * 
	 * @return The port number.
	 */
	public int getPort() {
		int port = getAddress().getPort();
		if( port == 0 && server != null ) {
			port = server.socket().getLocalPort();
		}
		return port;
	}

	/**
	 * Get the list of active drafts
	 * @return the available drafts for this server
	 */
	public List<Draft> getDraft() {
		return Collections.unmodifiableList( drafts );
	}

	// Runnable IMPLEMENTATION /////////////////////////////////////////////////
	public void run() {
		if (!doEnsureSingleThread()) {
			return;
		}
		if (!doSetupSelectorAndServerThread()) {
			return;
		}
		try {
			int iShutdownCount = 5;
			int selectTimeout = 0;
			while ( !selectorthread.isInterrupted() && iShutdownCount != 0) {
				SelectionKey key = null;
				WebSocketImpl conn = null;
				try {
					if (isclosed.get()) {
						selectTimeout = 5;
					}
					int keyCount = selector.select( selectTimeout );
					if (keyCount == 0 && isclosed.get()) {
						iShutdownCount--;
					}
					Set<SelectionKey> keys = selector.selectedKeys();
					Iterator<SelectionKey> i = keys.iterator();

					while ( i.hasNext() ) {
						key = i.next();
						conn = null;
						
						if( !key.isValid() ) {
							continue;
						}

						if( key.isAcceptable() ) {
							doAccept(key, i);
							continue;
						}

						if( key.isReadable() && !doRead(key, i)) {
							continue;
						}

						if( key.isWritable() ) {
							doWrite(key);
						}
					}
					doAdditionalRead();
				} catch ( CancelledKeyException e ) {
					// an other thread may cancel the key
				} catch ( ClosedByInterruptException e ) {
					return; // do the same stuff as when InterruptedException is thrown
				} catch ( IOException ex ) {
					if( key != null )
						key.cancel();
					handleIOException( key, conn, ex );
				} catch ( InterruptedException e ) {
					// FIXME controlled shutdown (e.g. take care of buffermanagement)
					Thread.currentThread().interrupt();
				}
			}
		} catch ( RuntimeException e ) {
			// should hopefully never occur
			handleFatal( null, e );
		} finally {
			doServerShutdown();
		}
	}

	/**
	 * Do an additional read
	 * @throws InterruptedException thrown by taking a buffer
	 * @throws IOException if an error happened during read
	 */
	private void doAdditionalRead() throws InterruptedException, IOException {
		WebSocketImpl conn;
		while ( !iqueue.isEmpty() ) {
			conn = iqueue.remove( 0 );
			WrappedByteChannel c = ((WrappedByteChannel) conn.getChannel() );
			ByteBuffer buf = takeBuffer();
			try {
				if( SocketChannelIOHelper.readMore( buf, conn, c ) )
					iqueue.add( conn );
				if( buf.hasRemaining() ) {
					conn.inQueue.put( buf );
					queue( conn );
				} else {
					pushBuffer( buf );
				}
			} catch ( IOException e ) {
				pushBuffer( buf );
				throw e;
			}
		}
	}

	/**
	 * Execute a accept operation
	 * @param key the selectionkey to read off
	 * @param i the iterator for the selection keys
	 * @throws InterruptedException  thrown by taking a buffer
	 * @throws IOException if an error happened during accept
	 */
	private void doAccept(SelectionKey key, Iterator<SelectionKey> i) throws IOException, InterruptedException {
		if( !onConnect( key ) ) {
			key.cancel();
			return;
		}

		SocketChannel channel = server.accept();
		if(channel==null){
			return;
		}
		channel.configureBlocking( false );
		Socket socket = channel.socket();
		socket.setTcpNoDelay( isTcpNoDelay() );
		socket.setKeepAlive( true );
		WebSocketImpl w = wsf.createWebSocket(this, drafts );
		w.setSelectionKey(channel.register( selector, SelectionKey.OP_READ, w ));
		try {
			w.setChannel( wsf.wrapChannel( channel, w.getSelectionKey() ));
			i.remove();
			allocateBuffers( w );
		} catch (IOException ex) {
			if( w.getSelectionKey() != null )
				w.getSelectionKey().cancel();

			handleIOException( w.getSelectionKey(), null, ex );
		}
	}

	/**
	 * Execute a read operation
	 * @param key the selectionkey to read off
	 * @param i the iterator for the selection keys
	 * @return true, if the read was successful, or false if there was an error
	 * @throws InterruptedException thrown by taking a buffer
	 * @throws IOException if an error happened during read
	 */
	private boolean doRead(SelectionKey key, Iterator<SelectionKey> i) throws InterruptedException, IOException {
		WebSocketImpl conn = (WebSocketImpl) key.attachment();
		ByteBuffer buf = takeBuffer();
		if(conn.getChannel() == null){
			if( key != null )
				key.cancel();

			handleIOException( key, conn, new IOException() );
			return false;
		}
		try {
			if( SocketChannelIOHelper.read( buf, conn, conn.getChannel() ) ) {
				if( buf.hasRemaining() ) {
					conn.inQueue.put( buf );
					queue( conn );
					i.remove();
					if( conn.getChannel() instanceof WrappedByteChannel) {
						if( ( (WrappedByteChannel) conn.getChannel() ).isNeedRead() ) {
							iqueue.add( conn );
						}
					}
				} else {
					pushBuffer(buf);
				}
			} else {
				pushBuffer( buf );
			}
		} catch ( IOException e ) {
			pushBuffer( buf );
			throw e;
		}
		return true;
	}

	/**
	 * Execute a write operation
	 * @param key the selectionkey to write on
	 * @throws IOException if an error happened during batch
	 */
	private void doWrite(SelectionKey key) throws IOException {
		WebSocketImpl conn = (WebSocketImpl) key.attachment();
		if( SocketChannelIOHelper.batch( conn, conn.getChannel() ) ) {
			if( key.isValid() )
				key.interestOps( SelectionKey.OP_READ );
		}
	}

	/**
	 * Setup the selector thread as well as basic server settings
	 * @return true, if everything was successful, false if some error happened
	 */
	private boolean doSetupSelectorAndServerThread() {
		selectorthread.setName( "WebSocketSelector-" + selectorthread.getId() );
		try {
			server = ServerSocketChannel.open();
			server.configureBlocking( false );
			ServerSocket socket = server.socket();
			socket.setReceiveBufferSize(WebSocketImpl.RCVBUF );
			socket.setReuseAddress( isReuseAddr() );
			socket.bind( address );
			selector = Selector.open();
			server.register( selector, server.validOps() );
			startConnectionLostTimer();
			onStart();
		} catch ( IOException ex ) {
			handleFatal( null, ex );
			return false;
		}
		return true;
	}

	/**
	 * The websocket server can only be started once
	 * @return true, if the server can be started, false if already a thread is running
	 */
	private boolean doEnsureSingleThread() {
		synchronized ( this ) {
			if( selectorthread != null )
				throw new IllegalStateException( getClass().getName() + " can only be started once." );
			selectorthread = Thread.currentThread();
			if( isclosed.get() ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Clean up everything after a shutdown
	 */
	private void doServerShutdown() {
		stopConnectionLostTimer();
		if( decoders != null ) {
			for( WebSocketWorker w : decoders ) {
				w.interrupt();
			}
		}
		if( selector != null ) {
			try {
				selector.close();
			} catch ( IOException e ) {
				log.error( "IOException during selector.close", e );
				onError( null, e );
			}
		}
		if( server != null ) {
			try {
				server.close();
			} catch ( IOException e ) {
				log.error( "IOException during server.close", e );
				onError( null, e );
			}
		}
	}

	protected void allocateBuffers( WebSocket c ) throws InterruptedException {
		if( queuesize.get() >= 2 * decoders.size() + 1 ) {
			return;
		}
		queuesize.incrementAndGet();
		buffers.put( createBuffer() );
	}

	protected void releaseBuffers( WebSocket c ) throws InterruptedException {
		// queuesize.decrementAndGet();
		// takeBuffer();
	}

	public ByteBuffer createBuffer() {
		return ByteBuffer.allocate(WebSocketImpl.RCVBUF );
	}

	protected void queue( WebSocketImpl ws ) throws InterruptedException {
		if( ws.getWorkerThread() == null ) {
			ws.setWorkerThread(decoders.get( queueinvokes % decoders.size() ));
			queueinvokes++;
		}
		ws.getWorkerThread().put( ws );
	}

	private ByteBuffer takeBuffer() throws InterruptedException {
		return buffers.take();
	}

	private void pushBuffer( ByteBuffer buf ) throws InterruptedException {
		if( buffers.size() > queuesize.intValue() )
			return;
		buffers.put( buf );
	}

	private void handleIOException( SelectionKey key, WebSocket conn, IOException ex ) {
		// onWebsocketError( conn, ex );// conn may be null here
		if( conn != null ) {
			conn.closeConnection( CloseFrame.ABNORMAL_CLOSE, ex.getMessage() );
		} else if( key != null ) {
			SelectableChannel channel = key.channel();
			if( channel != null && channel.isOpen() ) { // this could be the case if the IOException ex is a SSLException
				try {
					channel.close();
				} catch ( IOException e ) {
					// there is nothing that must be done here
				}
				log.trace("Connection closed because of exception",ex);
			}
		}
	}

	private void handleFatal( WebSocket conn, Exception e ) {
		log.error( "Shutdown due to fatal error", e );
		onError( conn, e );
		//Shutting down WebSocketWorkers, see #222
		if( decoders != null ) {
			for( WebSocketWorker w : decoders ) {
				w.interrupt();
			}
		}
		if (selectorthread != null) {
			selectorthread.interrupt();
		}
		try {
			stop();
		} catch ( IOException e1 ) {
			log.error( "Error during shutdown", e1 );
			onError( null, e1 );
		} catch ( InterruptedException e1 ) {
			Thread.currentThread().interrupt();
			log.error( "Interrupt during stop", e );
			onError( null, e1 );
		}
	}

	@Override
	public final void onWebsocketMessage( WebSocket conn, String message ) {
		onMessage( conn, message );
	}


	@Override
	public final void onWebsocketMessage( WebSocket conn, ByteBuffer blob ) {
		onMessage( conn, blob );
	}

	@Override
	public final void onWebsocketOpen( WebSocket conn, Handshakedata handshake ) {
		if( addConnection( conn ) ) {
			onOpen( conn, (ClientHandshake) handshake );
		}
	}

	@Override
	public final void onWebsocketClose( WebSocket conn, int code, String reason, boolean remote ) {
		selector.wakeup();
		try {
			if( removeConnection( conn ) ) {
				onClose( conn, code, reason, remote );
			}
		} finally {
			try {
				releaseBuffers( conn );
			} catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
			}
		}

	}

	/**
	 * This method performs remove operations on the connection and therefore also gives control over whether the operation shall be synchronized
	 * <p>
	 * {@link #WebSocketServer(InetSocketAddress, int, List, Collection)} allows to specify a collection which will be used to store current connections in.<br>
	 * Depending on the type on the connection, modifications of that collection may have to be synchronized.
	 * @param ws The Webscoket connection which should be removed
	 * @return Removing connection successful
	 */
	protected boolean removeConnection( WebSocket ws ) {
		boolean removed = false;
		synchronized ( connections ) {
			if (this.connections.contains( ws )) {
				removed = this.connections.remove( ws );
			} else {
				//Don't throw an assert error if the ws is not in the list. e.g. when the other endpoint did not send any handshake. see #512
				log.trace("Removing connection which is not in the connections collection! Possible no handshake recieved! {}", ws);
			}
		}
		if( isclosed.get() && connections.size() == 0 ) {
			selectorthread.interrupt();
		}
		return removed;
	}
	@Override
	public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request ) throws
                                                                                                                                  InvalidDataException {
		return super.onWebsocketHandshakeReceivedAsServer( conn, draft, request );
	}

	/**
	 * @see #removeConnection(WebSocket)
	 * @param ws the Webscoket connection which should be added
	 * @return Adding connection successful
	 */
	protected boolean addConnection( WebSocket ws ) {
		if( !isclosed.get() ) {
			synchronized ( connections ) {
				return this.connections.add( ws );
			}
		} else {
			// This case will happen when a new connection gets ready while the server is already stopping.
			ws.close( CloseFrame.GOING_AWAY );
			return true;// for consistency sake we will make sure that both onOpen will be called
		}
	}

	@Override
	public final void onWebsocketError( WebSocket conn, Exception ex ) {
		onError( conn, ex );
	}

	@Override
	public final void onWriteDemand( WebSocket w ) {
		WebSocketImpl conn = (WebSocketImpl) w;
		try {
			conn.getSelectionKey().interestOps( SelectionKey.OP_READ | SelectionKey.OP_WRITE );
		} catch ( CancelledKeyException e ) {
			// the thread which cancels key is responsible for possible cleanup
			conn.outQueue.clear();
		}
		selector.wakeup();
	}

	@Override
	public void onWebsocketCloseInitiated( WebSocket conn, int code, String reason ) {
		onCloseInitiated( conn, code, reason );
	}

	@Override
	public void onWebsocketClosing( WebSocket conn, int code, String reason, boolean remote ) {
		onClosing( conn, code, reason, remote );

	}

	public void onCloseInitiated( WebSocket conn, int code, String reason ) {
	}

	public void onClosing( WebSocket conn, int code, String reason, boolean remote ) {

	}

	public final void setWebSocketFactory( WebSocketServerFactory wsf ) {
		if (this.wsf != null)
			this.wsf.close();
		this.wsf = wsf;
	}

	public final WebSocketFactory getWebSocketFactory() {
		return wsf;
	}

	/**
	 * Returns whether a new connection shall be accepted or not.<br>
	 * Therefore method is well suited to implement some kind of connection limitation.<br>
	 * 
	 * @see #onOpen(WebSocket, ClientHandshake)
         * @see #onWebsocketHandshakeReceivedAsServer(WebSocket, Draft, ClientHandshake)
	 * @param key the SelectionKey for the new connection
	 * @return Can this new connection be accepted
	 **/
	protected boolean onConnect( SelectionKey key ) {
		return true;
	}

	/**
	 * Getter to return the socket used by this specific connection
	 * @param conn The specific connection
	 * @return The socket used by this connection
	 */
	private Socket getSocket( WebSocket conn ) {
		WebSocketImpl impl = (WebSocketImpl) conn;
		return ( (SocketChannel) impl.getSelectionKey().channel() ).socket();
	}

	@Override
	public InetSocketAddress getLocalSocketAddress( WebSocket conn ) {
		return (InetSocketAddress) getSocket( conn ).getLocalSocketAddress();
	}

	@Override
	public InetSocketAddress getRemoteSocketAddress( WebSocket conn ) {
		return (InetSocketAddress) getSocket( conn ).getRemoteSocketAddress();
	}

	/** Called after an opening handshake has been performed and the given websocket is ready to be written on.
	 * @param conn The <tt>WebSocket</tt> instance this event is occuring on.
	 * @param handshake The handshake of the websocket instance
	 */
	public abstract void onOpen( WebSocket conn, ClientHandshake handshake );
	/**
	 * Called after the websocket connection has been closed.
	 *
	 * @param conn The <tt>WebSocket</tt> instance this event is occuring on.
	 * @param code
	 *            The codes can be looked up here: {@link CloseFrame}
	 * @param reason
	 *            Additional information string
	 * @param remote
	 *            Returns whether or not the closing of the connection was initiated by the remote host.
	 **/
	public abstract void onClose( WebSocket conn, int code, String reason, boolean remote );
	/**
	 * Callback for string messages received from the remote host
	 * 
	 * @see #onMessage(WebSocket, ByteBuffer)
	 * @param conn The <tt>WebSocket</tt> instance this event is occuring on.
	 * @param message The UTF-8 decoded message that was received.
	 **/
	public abstract void onMessage( WebSocket conn, String message );
	/**
	 * Called when errors occurs. If an error causes the websocket connection to fail {@link #onClose(WebSocket, int, String, boolean)} will be called additionally.<br>
	 * This method will be called primarily because of IO or protocol errors.<br>
	 * If the given exception is an RuntimeException that probably means that you encountered a bug.<br>
	 * 
	 * @param conn Can be null if there error does not belong to one specific websocket. For example if the servers port could not be bound.
	 * @param ex The exception causing this error
	 **/
	public abstract void onError( WebSocket conn, Exception ex );

	/**
	 * Called when the server started up successfully.
	 *
	 * If any error occured, onError is called instead.
	 */
	public abstract void onStart();

	/**
	 * Callback for binary messages received from the remote host
	 * 
	 * @see #onMessage(WebSocket, ByteBuffer)
	 *
	 *  @param conn
	 *            The <tt>WebSocket</tt> instance this event is occurring on.
	 * @param message
	 *            The binary message that was received.
	 **/
	public void onMessage( WebSocket conn, ByteBuffer message ) {
	}

	/**
	 * Send a text to all connected endpoints
	 * @param text the text to send to the endpoints
	 */
	public void broadcast(String text) {
		broadcast( text, connections );
	}

	/**
	 * Send a byte array to all connected endpoints
	 * @param data the data to send to the endpoints
	 */
	public void broadcast(byte[] data) {
		broadcast( data, connections );
	}

	/**
	 * Send a ByteBuffer to all connected endpoints
	 * @param data the data to send to the endpoints
	 */
	public void broadcast(ByteBuffer data) {
		broadcast(data, connections);
	}

	/**
	 * Send a byte array to a specific collection of websocket connections
	 * @param data the data to send to the endpoints
	 * @param clients a collection of endpoints to whom the text has to be send
	 */
	public void broadcast(byte[] data, Collection<WebSocket> clients) {
		if (data == null || clients == null) {
			throw new IllegalArgumentException();
		}
		broadcast(ByteBuffer.wrap(data), clients);
	}

	/**
	 * Send a ByteBuffer to a specific collection of websocket connections
	 * @param data the data to send to the endpoints
	 * @param clients a collection of endpoints to whom the text has to be send
	 */
	public void broadcast(ByteBuffer data, Collection<WebSocket> clients) {
		if (data == null || clients == null) {
			throw new IllegalArgumentException();
		}
		doBroadcast(data, clients);
	}

	/**
	 * Send a text to a specific collection of websocket connections
	 * @param text the text to send to the endpoints
	 * @param clients a collection of endpoints to whom the text has to be send
	 */
	public void broadcast(String text, Collection<WebSocket> clients) {
		if (text == null || clients == null) {
			throw new IllegalArgumentException();
		}
		doBroadcast(text, clients);
	}

	/**
	 * Private method to cache all the frames to improve memory footprint and conversion time
	 * @param data the data to broadcast
	 * @param clients the clients to send the message to
	 */
	private void doBroadcast(Object data, Collection<WebSocket> clients) {
		String sData = null;
		if (data instanceof String) {
			sData = (String)data;
		}
		ByteBuffer bData = null;
		if (data instanceof ByteBuffer) {
			bData = (ByteBuffer)data;
		}
		if (sData == null && bData == null) {
			return;
		}
		Map<Draft, List<Framedata>> draftFrames = new HashMap<Draft, List<Framedata>>();
		for( WebSocket client : clients ) {
			if( client != null ) {
				Draft draft = client.getDraft();
				fillFrames(draft, draftFrames, sData, bData);
				try {
					client.sendFrame( draftFrames.get( draft ) );
				} catch ( WebsocketNotConnectedException e ) {
					//Ignore this exception in this case
				}
			}
		}
	}

	/**
	 * Fills the draftFrames with new data for the broadcast
	 * @param draft The draft to use
	 * @param draftFrames The list of frames per draft to fill
	 * @param sData the string data, can be null
	 * @param bData the bytebuffer data, can be null
	 */
	private void fillFrames(Draft draft, Map<Draft, List<Framedata>> draftFrames, String sData, ByteBuffer bData) {
		if( !draftFrames.containsKey( draft ) ) {
			List<Framedata> frames = null;
			if (sData != null) {
				frames = draft.createFrames( sData, false );
			}
			if (bData != null) {
				frames = draft.createFrames( bData, false );
			}
			if (frames != null) {
				draftFrames.put(draft, frames);
			}
		}
	}

	/**
	 * This class is used to process incoming data
	 */
	public class WebSocketWorker extends Thread {

		private BlockingQueue<WebSocketImpl> iqueue;

		public WebSocketWorker() {
			iqueue = new LinkedBlockingQueue<WebSocketImpl>();
			setName( "WebSocketWorker-" + getId() );
			setUncaughtExceptionHandler( new UncaughtExceptionHandler() {
				@Override
				public void uncaughtException( Thread t, Throwable e ) {
					log.error("Uncaught exception in thread {}: {}", t.getName(), e);
				}
			} );
		}

		public void put( WebSocketImpl ws ) throws InterruptedException {
			iqueue.put( ws );
		}

		@Override
		public void run() {
			WebSocketImpl ws = null;
			try {
				while ( true ) {
					ByteBuffer buf;
					ws = iqueue.take();
					buf = ws.inQueue.poll();
					assert ( buf != null );
					doDecode(ws, buf);
					ws = null;
				}
			} catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
			} catch ( RuntimeException e ) {
				handleFatal( ws, e );
			}
		}

		/**
		 * call ws.decode on the bytebuffer
		 * @param ws the Websocket
		 * @param buf the buffer to decode to
		 * @throws InterruptedException thrown by pushBuffer
		 */
		private void doDecode(WebSocketImpl ws, ByteBuffer buf) throws InterruptedException {
			try {
				ws.decode( buf );
			} catch(Exception e){
				log.error("Error while reading from remote connection", e);
			}
			finally {
				pushBuffer( buf );
			}
		}
	}
}
