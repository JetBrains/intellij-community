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

package org.java_websocket;

import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.*;
import org.java_websocket.exceptions.*;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.PingFrame;
import org.java_websocket.handshake.*;
import org.java_websocket.server.WebSocketServer.WebSocketWorker;
import org.java_websocket.util.Charsetfunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents one end (client or server) of a single WebSocketImpl connection.
 * Takes care of the "handshake" phase, then allows for easy sending of
 * text frames, and receiving frames through an event-based model.
 */
public class WebSocketImpl implements WebSocket {

	/**
	 * The default port of WebSockets, as defined in the spec. If the nullary
	 * constructor is used, DEFAULT_PORT will be the port the WebSocketServer
	 * is binded to. Note that ports under 1024 usually require root permissions.
	 */
	public static final int DEFAULT_PORT = 80;

	/**
	 * The default wss port of WebSockets, as defined in the spec. If the nullary
	 * constructor is used, DEFAULT_WSS_PORT will be the port the WebSocketServer
	 * is binded to. Note that ports under 1024 usually require root permissions.
	 */
	public static final int DEFAULT_WSS_PORT = 443;

	/**
	 * Initial buffer size
	 */
	public static final int RCVBUF = 16384;

	/**
	 * Logger instance
	 *
	 * @since 1.4.0
	 */
	private static final Logger log = LoggerFactory.getLogger(WebSocketImpl.class);

	/**
	 * Queue of buffers that need to be sent to the client.
	 */
	public final BlockingQueue<ByteBuffer> outQueue;
	/**
	 * Queue of buffers that need to be processed
	 */
	public final BlockingQueue<ByteBuffer> inQueue;
	/**
	 * The listener to notify of WebSocket events.
	 */
	private final WebSocketListener wsl;

	private SelectionKey key;

	/**
	 * the possibly wrapped channel object whose selection is controlled by {@link #key}
	 */
	private ByteChannel channel;
	/**
	 * Helper variable meant to store the thread which ( exclusively ) triggers this objects decode method.
	 **/
	private volatile WebSocketWorker workerThread;
	/**
	 * When true no further frames may be submitted to be sent
	 */
	private volatile boolean flushandclosestate = false;

	/**
	 * The current state of the connection
	 */
	private volatile ReadyState readyState = ReadyState.NOT_YET_CONNECTED;

	/**
	 * A list of drafts available for this websocket
	 */
	private List<Draft> knownDrafts;

	/**
	 * The draft which is used by this websocket
	 */
	private Draft draft = null;

	/**
	 * The role which this websocket takes in the connection
	 */
	private Role role;

	/**
	 * the bytes of an incomplete received handshake
	 */
	private ByteBuffer tmpHandshakeBytes = ByteBuffer.allocate( 0 );

	/**
	 * stores the handshake sent by this websocket ( Role.CLIENT only )
	 */
	private ClientHandshake handshakerequest = null;

	private String closemessage = null;
	private Integer closecode = null;
	private Boolean closedremotely = null;

	private String resourceDescriptor = null;

	/**
	 * Attribute, when the last pong was recieved
	 */
	private long lastPong = System.currentTimeMillis();

	/**
	 * Attribut to synchronize the write
	 */
	private final Object synchronizeWriteObject = new Object();

	/**
	 * Attribute to cache a ping frame
	 */
	private PingFrame pingFrame;

	/**
	 * Attribute to store connection attachment
	 * @since 1.3.7
	 */
	private Object attachment;

	/**
	 * Creates a websocket with server role
	 *
	 * @param listener The listener for this instance
	 * @param drafts   The drafts which should be used
	 */
	public WebSocketImpl( WebSocketListener listener, List<Draft> drafts ) {
		this( listener, ( Draft ) null );
		this.role = Role.SERVER;
		// draft.copyInstance will be called when the draft is first needed
		if( drafts == null || drafts.isEmpty() ) {
			knownDrafts = new ArrayList<Draft>();
			knownDrafts.add( new Draft_6455() );
		} else {
			knownDrafts = drafts;
		}
	}

	/**
	 * creates a websocket with client role
	 *
	 * @param listener The listener for this instance
	 * @param draft    The draft which should be used
	 */
	public WebSocketImpl( WebSocketListener listener, Draft draft ) {
		if( listener == null || ( draft == null && role == Role.SERVER ) )// socket can be null because we want do be able to create the object without already having a bound channel
			throw new IllegalArgumentException( "parameters must not be null" );
		this.outQueue = new LinkedBlockingQueue<ByteBuffer>();
		inQueue = new LinkedBlockingQueue<ByteBuffer>();
		this.wsl = listener;
		this.role = Role.CLIENT;
		if( draft != null )
			this.draft = draft.copyInstance();
	}

	/**
	 * Method to decode the provided ByteBuffer
	 *
	 * @param socketBuffer the ByteBuffer to decode
	 */
	public void decode( ByteBuffer socketBuffer ) {
		assert ( socketBuffer.hasRemaining() );
		log.trace( "process({}): ({})", socketBuffer.remaining(),  ( socketBuffer.remaining() > 1000 ? "too big to display" : new String( socketBuffer.array(), socketBuffer.position(), socketBuffer.remaining() ) ));

		if( readyState != ReadyState.NOT_YET_CONNECTED ) {
			if( readyState == ReadyState.OPEN ) {
				decodeFrames( socketBuffer );
			}
		} else {
			if( decodeHandshake( socketBuffer ) && (!isClosing() && !isClosed())) {
				assert ( tmpHandshakeBytes.hasRemaining() != socketBuffer.hasRemaining() || !socketBuffer.hasRemaining() ); // the buffers will never have remaining bytes at the same time
				if( socketBuffer.hasRemaining() ) {
					decodeFrames( socketBuffer );
				} else if( tmpHandshakeBytes.hasRemaining() ) {
					decodeFrames( tmpHandshakeBytes );
				}
			}
		}
	}

	/**
	 * Returns whether the handshake phase has is completed.
	 * In case of a broken handshake this will be never the case.
	 **/
	private boolean decodeHandshake( ByteBuffer socketBufferNew ) {
		ByteBuffer socketBuffer;
		if( tmpHandshakeBytes.capacity() == 0 ) {
			socketBuffer = socketBufferNew;
		} else {
			if( tmpHandshakeBytes.remaining() < socketBufferNew.remaining() ) {
				ByteBuffer buf = ByteBuffer.allocate( tmpHandshakeBytes.capacity() + socketBufferNew.remaining() );
				tmpHandshakeBytes.flip();
				buf.put( tmpHandshakeBytes );
				tmpHandshakeBytes = buf;
			}

			tmpHandshakeBytes.put( socketBufferNew );
			tmpHandshakeBytes.flip();
			socketBuffer = tmpHandshakeBytes;
		}
		socketBuffer.mark();
		try {
			HandshakeState handshakestate;
			try {
				if( role == Role.SERVER ) {
					if( draft == null ) {
						for( Draft d : knownDrafts ) {
							d = d.copyInstance();
							try {
								d.setParseMode( role );
								socketBuffer.reset();
								Handshakedata tmphandshake = d.translateHandshake( socketBuffer );
								if( !( tmphandshake instanceof ClientHandshake ) ) {
									log.trace("Closing due to wrong handshake");
									closeConnectionDueToWrongHandshake( new InvalidDataException( CloseFrame.PROTOCOL_ERROR, "wrong http function" ) );
									return false;
								}
								ClientHandshake handshake = ( ClientHandshake ) tmphandshake;
								handshakestate = d.acceptHandshakeAsServer( handshake );
								if( handshakestate == HandshakeState.MATCHED ) {
									resourceDescriptor = handshake.getResourceDescriptor();
									ServerHandshakeBuilder response;
									try {
										response = wsl.onWebsocketHandshakeReceivedAsServer( this, d, handshake );
									} catch ( InvalidDataException e ) {
										log.trace("Closing due to wrong handshake. Possible handshake rejection", e);
										closeConnectionDueToWrongHandshake( e );
										return false;
									} catch ( RuntimeException e ) {
										log.error("Closing due to internal server error", e);
										wsl.onWebsocketError( this, e );
										closeConnectionDueToInternalServerError( e );
										return false;
									}
									write( d.createHandshake( d.postProcessHandshakeResponseAsServer( handshake, response ) ) );
									draft = d;
									open( handshake );
									return true;
								}
							} catch ( InvalidHandshakeException e ) {
								// go on with an other draft
							}
						}
						if( draft == null ) {
							log.trace("Closing due to protocol error: no draft matches");
							closeConnectionDueToWrongHandshake( new InvalidDataException( CloseFrame.PROTOCOL_ERROR, "no draft matches" ) );
						}
						return false;
					} else {
						// special case for multiple step handshakes
						Handshakedata tmphandshake = draft.translateHandshake( socketBuffer );
						if( !( tmphandshake instanceof ClientHandshake ) ) {
							log.trace("Closing due to protocol error: wrong http function");
							flushAndClose( CloseFrame.PROTOCOL_ERROR, "wrong http function", false );
							return false;
						}
						ClientHandshake handshake = ( ClientHandshake ) tmphandshake;
						handshakestate = draft.acceptHandshakeAsServer( handshake );

						if( handshakestate == HandshakeState.MATCHED ) {
							open( handshake );
							return true;
						} else {
							log.trace("Closing due to protocol error: the handshake did finally not match");
							close( CloseFrame.PROTOCOL_ERROR, "the handshake did finally not match" );
						}
						return false;
					}
				} else if( role == Role.CLIENT ) {
					draft.setParseMode( role );
					Handshakedata tmphandshake = draft.translateHandshake( socketBuffer );
					if( !( tmphandshake instanceof ServerHandshake ) ) {
						log.trace("Closing due to protocol error: wrong http function");
						flushAndClose( CloseFrame.PROTOCOL_ERROR, "wrong http function", false );
						return false;
					}
					ServerHandshake handshake = ( ServerHandshake ) tmphandshake;
					handshakestate = draft.acceptHandshakeAsClient( handshakerequest, handshake );
					if( handshakestate == HandshakeState.MATCHED ) {
						try {
							wsl.onWebsocketHandshakeReceivedAsClient( this, handshakerequest, handshake );
						} catch ( InvalidDataException e ) {
							log.trace("Closing due to invalid data exception. Possible handshake rejection", e);
							flushAndClose( e.getCloseCode(), e.getMessage(), false );
							return false;
						} catch ( RuntimeException e ) {
							log.error("Closing since client was never connected", e);
							wsl.onWebsocketError( this, e );
							flushAndClose( CloseFrame.NEVER_CONNECTED, e.getMessage(), false );
							return false;
						}
						open( handshake );
						return true;
					} else {
						log.trace("Closing due to protocol error: draft {} refuses handshake", draft );
						close( CloseFrame.PROTOCOL_ERROR, "draft " + draft + " refuses handshake" );
					}
				}
			} catch ( InvalidHandshakeException e ) {
				log.trace("Closing due to invalid handshake", e);
				close( e );
			}
		} catch ( IncompleteHandshakeException e ) {
			if( tmpHandshakeBytes.capacity() == 0 ) {
				socketBuffer.reset();
				int newsize = e.getPreferredSize();
				if( newsize == 0 ) {
					newsize = socketBuffer.capacity() + 16;
				} else {
					assert ( e.getPreferredSize() >= socketBuffer.remaining() );
				}
				tmpHandshakeBytes = ByteBuffer.allocate( newsize );

				tmpHandshakeBytes.put( socketBufferNew );
				// tmpHandshakeBytes.flip();
			} else {
				tmpHandshakeBytes.position( tmpHandshakeBytes.limit() );
				tmpHandshakeBytes.limit( tmpHandshakeBytes.capacity() );
			}
		}
		return false;
	}

	private void decodeFrames( ByteBuffer socketBuffer ) {
		List<Framedata> frames;
		try {
			frames = draft.translateFrame( socketBuffer );
			for( Framedata f : frames ) {
				log.trace( "matched frame: {}" , f );
				draft.processFrame( this, f );
			}
		} catch ( LimitExceededException e ) {
			if (e.getLimit() == Integer.MAX_VALUE) {
				log.error("Closing due to invalid size of frame", e);
				wsl.onWebsocketError(this, e);
			}
			close(e);
		} catch ( InvalidDataException e ) {
			log.error("Closing due to invalid data in frame", e);
			wsl.onWebsocketError( this, e );
			close(e);
		}
	}

	/**
	 * Close the connection if the received handshake was not correct
	 *
	 * @param exception the InvalidDataException causing this problem
	 */
	private void closeConnectionDueToWrongHandshake( InvalidDataException exception ) {
		write( generateHttpResponseDueToError( 404 ) );
		flushAndClose( exception.getCloseCode(), exception.getMessage(), false );
	}

	/**
	 * Close the connection if there was a server error by a RuntimeException
	 *
	 * @param exception the RuntimeException causing this problem
	 */
	private void closeConnectionDueToInternalServerError( RuntimeException exception ) {
		write( generateHttpResponseDueToError( 500 ) );
		flushAndClose( CloseFrame.NEVER_CONNECTED, exception.getMessage(), false );
	}

	/**
	 * Generate a simple response for the corresponding endpoint to indicate some error
	 *
	 * @param errorCode the http error code
	 * @return the complete response as ByteBuffer
	 */
	private ByteBuffer generateHttpResponseDueToError( int errorCode ) {
		String errorCodeDescription;
		switch(errorCode) {
			case 404:
				errorCodeDescription = "404 WebSocket Upgrade Failure";
				break;
			case 500:
			default:
				errorCodeDescription = "500 Internal Server Error";
		}
		return ByteBuffer.wrap( Charsetfunctions.asciiBytes( "HTTP/1.1 " + errorCodeDescription + "\r\nContent-Type: text/html\nServer: TooTallNate Java-WebSocket\r\nContent-Length: " + ( 48 + errorCodeDescription.length() ) + "\r\n\r\n<html><head></head><body><h1>" + errorCodeDescription + "</h1></body></html>" ) );
	}

	public synchronized void close( int code, String message, boolean remote ) {
		if( readyState != ReadyState.CLOSING && readyState != ReadyState.CLOSED ) {
			if( readyState == ReadyState.OPEN ) {
				if( code == CloseFrame.ABNORMAL_CLOSE ) {
					assert ( !remote );
					readyState = ReadyState.CLOSING ;
					flushAndClose( code, message, false );
					return;
				}
				if( draft.getCloseHandshakeType() != CloseHandshakeType.NONE ) {
					try {
						if( !remote ) {
							try {
								wsl.onWebsocketCloseInitiated( this, code, message );
							} catch ( RuntimeException e ) {
								wsl.onWebsocketError( this, e );
							}
						}
						if( isOpen() ) {
							CloseFrame closeFrame = new CloseFrame();
							closeFrame.setReason( message );
							closeFrame.setCode( code );
							closeFrame.isValid();
							sendFrame( closeFrame );
						}
					} catch ( InvalidDataException e ) {
						log.error("generated frame is invalid", e);
						wsl.onWebsocketError( this, e );
						flushAndClose( CloseFrame.ABNORMAL_CLOSE, "generated frame is invalid", false );
					}
				}
				flushAndClose( code, message, remote );
			} else if( code == CloseFrame.FLASHPOLICY ) {
				assert ( remote );
				flushAndClose( CloseFrame.FLASHPOLICY, message, true );
			} else if( code == CloseFrame.PROTOCOL_ERROR ) { // this endpoint found a PROTOCOL_ERROR
				flushAndClose( code, message, remote );
			} else {
				flushAndClose( CloseFrame.NEVER_CONNECTED, message, false );
			}
			readyState = ReadyState.CLOSING;
			tmpHandshakeBytes = null;
			return;
		}
	}

	@Override
	public void close( int code, String message ) {
		close( code, message, false );
	}

	/**
	 * This will close the connection immediately without a proper close handshake.
	 * The code and the message therefore won't be transfered over the wire also they will be forwarded to onClose/onWebsocketClose.
	 *
	 * @param code    the closing code
	 * @param message the closing message
	 * @param remote  Indicates who "generated" <code>code</code>.<br>
	 *                <code>true</code> means that this endpoint received the <code>code</code> from the other endpoint.<br>
	 *                false means this endpoint decided to send the given code,<br>
	 *                <code>remote</code> may also be true if this endpoint started the closing handshake since the other endpoint may not simply echo the <code>code</code> but close the connection the same time this endpoint does do but with an other <code>code</code>. <br>
	 **/
	public synchronized void closeConnection( int code, String message, boolean remote ) {
		if( readyState == ReadyState.CLOSED ) {
			return;
		}
		//Methods like eot() call this method without calling onClose(). Due to that reason we have to adjust the ReadyState manually
		if( readyState == ReadyState.OPEN ) {
			if( code == CloseFrame.ABNORMAL_CLOSE ) {
				readyState = ReadyState.CLOSING;
			}
		}
		if( key != null ) {
			// key.attach( null ); //see issue #114
			key.cancel();
		}
		if( channel != null ) {
			try {
				channel.close();
			} catch ( IOException e ) {
				if( e.getMessage().equals( "Broken pipe" ) ) {
					log.trace( "Caught IOException: Broken pipe during closeConnection()", e );
				} else {
					log.error("Exception during channel.close()", e);
					wsl.onWebsocketError( this, e );
				}
			}
		}
		try {
			this.wsl.onWebsocketClose( this, code, message, remote );
		} catch ( RuntimeException e ) {

			wsl.onWebsocketError( this, e );
		}
		if( draft != null )
			draft.reset();
		handshakerequest = null;
		readyState = ReadyState.CLOSED;
	}

	protected void closeConnection( int code, boolean remote ) {
		closeConnection( code, "", remote );
	}

	public void closeConnection() {
		if( closedremotely == null ) {
			throw new IllegalStateException( "this method must be used in conjunction with flushAndClose" );
		}
		closeConnection( closecode, closemessage, closedremotely );
	}

	public void closeConnection( int code, String message ) {
		closeConnection( code, message, false );
	}

	public synchronized void flushAndClose( int code, String message, boolean remote ) {
		if( flushandclosestate ) {
			return;
		}
		closecode = code;
		closemessage = message;
		closedremotely = remote;

		flushandclosestate = true;

		wsl.onWriteDemand( this ); // ensures that all outgoing frames are flushed before closing the connection
		try {
			wsl.onWebsocketClosing( this, code, message, remote );
		} catch ( RuntimeException e ) {
			log.error("Exception in onWebsocketClosing", e);
			wsl.onWebsocketError( this, e );
		}
		if( draft != null )
			draft.reset();
		handshakerequest = null;
	}

	public void eot() {
		if( readyState == ReadyState.NOT_YET_CONNECTED ) {
			closeConnection( CloseFrame.NEVER_CONNECTED, true );
		} else if( flushandclosestate ) {
			closeConnection( closecode, closemessage, closedremotely );
		} else if( draft.getCloseHandshakeType() == CloseHandshakeType.NONE ) {
			closeConnection( CloseFrame.NORMAL, true );
		} else if( draft.getCloseHandshakeType() == CloseHandshakeType.ONEWAY ) {
			if( role == Role.SERVER )
				closeConnection( CloseFrame.ABNORMAL_CLOSE, true );
			else
				closeConnection( CloseFrame.NORMAL, true );
		} else {
			closeConnection( CloseFrame.ABNORMAL_CLOSE, true );
		}
	}

	@Override
	public void close( int code ) {
		close( code, "", false );
	}

	public void close( InvalidDataException e ) {
		close( e.getCloseCode(), e.getMessage(), false );
	}

	/**
	 * Send Text data to the other end.
	 *
	 * @throws WebsocketNotConnectedException websocket is not yet connected
	 */
	@Override
	public void send( String text ) {
		if( text == null )
			throw new IllegalArgumentException( "Cannot send 'null' data to a WebSocketImpl." );
		send( draft.createFrames( text, role == Role.CLIENT ) );
	}

	/**
	 * Send Binary data (plain bytes) to the other end.
	 *
	 * @throws IllegalArgumentException the data is null
	 * @throws WebsocketNotConnectedException websocket is not yet connected
	 */
	@Override
	public void send( ByteBuffer bytes ) {
		if( bytes == null )
			throw new IllegalArgumentException( "Cannot send 'null' data to a WebSocketImpl." );
		send( draft.createFrames( bytes, role == Role.CLIENT ) );
	}

	@Override
	public void send( byte[] bytes ) {
		send( ByteBuffer.wrap( bytes ) );
	}

	private void send( Collection<Framedata> frames ) {
		if( !isOpen() ) {
			throw new WebsocketNotConnectedException();
		}
		if( frames == null ) {
			throw new IllegalArgumentException();
		}
		ArrayList<ByteBuffer> outgoingFrames = new ArrayList<ByteBuffer>();
		for( Framedata f : frames ) {
			log.trace( "send frame: {}", f);
			outgoingFrames.add( draft.createBinaryFrame( f ) );
		}
		write( outgoingFrames );
	}

	@Override
	public void sendFragmentedFrame(Opcode op, ByteBuffer buffer, boolean fin ) {
		send( draft.continuousFrame( op, buffer, fin ) );
	}

	@Override
	public void sendFrame( Collection<Framedata> frames ) {
		send( frames );
	}

	@Override
	public void sendFrame( Framedata framedata ) {
		send( Collections.singletonList( framedata ) );
	}

	public void sendPing() {
		if( pingFrame == null ) {
			pingFrame = new PingFrame();
		}
		sendFrame( pingFrame );
	}

	@Override
	public boolean hasBufferedData() {
		return !this.outQueue.isEmpty();
	}

	public void startHandshake( ClientHandshakeBuilder handshakedata ) throws InvalidHandshakeException {
		// Store the Handshake Request we are about to send
		this.handshakerequest = draft.postProcessHandshakeRequestAsClient( handshakedata );

		resourceDescriptor = handshakedata.getResourceDescriptor();
		assert ( resourceDescriptor != null );

		// Notify Listener
		try {
			wsl.onWebsocketHandshakeSentAsClient( this, this.handshakerequest );
		} catch ( InvalidDataException e ) {
			// Stop if the client code throws an exception
			throw new InvalidHandshakeException( "Handshake data rejected by client." );
		} catch ( RuntimeException e ) {
			log.error("Exception in startHandshake", e);
			wsl.onWebsocketError( this, e );
			throw new InvalidHandshakeException( "rejected because of " + e );
		}

		// Send
		write( draft.createHandshake( this.handshakerequest ) );
	}

	private void write( ByteBuffer buf ) {
		log.trace( "write({}): {}", buf.remaining(), buf.remaining() > 1000 ? "too big to display" : new String( buf.array() ));

		outQueue.add( buf );
		wsl.onWriteDemand( this );
	}

	/**
	 * Write a list of bytebuffer (frames in binary form) into the outgoing queue
	 *
	 * @param bufs the list of bytebuffer
	 */
	private void write( List<ByteBuffer> bufs ) {
		synchronized(synchronizeWriteObject) {
			for( ByteBuffer b : bufs ) {
				write( b );
			}
		}
	}

	private void open( Handshakedata d ) {
		log.trace( "open using draft: {}",draft );
		readyState = ReadyState.OPEN;
		try {
			wsl.onWebsocketOpen( this, d );
		} catch ( RuntimeException e ) {
			wsl.onWebsocketError( this, e );
		}
	}

	@Override
	public boolean isOpen() {
		return readyState == ReadyState.OPEN;
	}

	@Override
	public boolean isClosing() {
		return readyState == ReadyState.CLOSING;
	}

	@Override
	public boolean isFlushAndClose() {
		return flushandclosestate;
	}

	@Override
	public boolean isClosed() {
		return readyState == ReadyState.CLOSED;
	}

	@Override
	public ReadyState getReadyState() {
		return readyState;
	}

	/**
	 * @param key the selection key of this implementation
	 */
	public void setSelectionKey(SelectionKey key) {
		this.key = key;
	}

	/**
	 * @return the selection key of this implementation
	 */
	public SelectionKey getSelectionKey() {
		return key;
	}

	@Override
	public String toString() {
		return super.toString(); // its nice to be able to set breakpoints here
	}

	@Override
	public InetSocketAddress getRemoteSocketAddress() {
		return wsl.getRemoteSocketAddress( this );
	}

	@Override
	public InetSocketAddress getLocalSocketAddress() {
		return wsl.getLocalSocketAddress( this );
	}

	@Override
	public Draft getDraft() {
		return draft;
	}

	@Override
	public void close() {
		close( CloseFrame.NORMAL );
	}

	@Override
	public String getResourceDescriptor() {
		return resourceDescriptor;
	}

	/**
	 * Getter for the last pong recieved
	 *
	 * @return the timestamp for the last recieved pong
	 */
	long getLastPong() {
		return lastPong;
	}

	/**
	 * Update the timestamp when the last pong was received
	 */
	public void updateLastPong() {
		this.lastPong = System.currentTimeMillis();
	}

	/**
	 * Getter for the websocket listener
	 *
	 * @return the websocket listener associated with this instance
	 */
	public WebSocketListener getWebSocketListener() {
		return wsl;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getAttachment() {
		return (T) attachment;
	}

	@Override
	public <T> void setAttachment(T attachment) {
		this.attachment = attachment;
	}

	public ByteChannel getChannel() {
		return channel;
	}

	public void setChannel(ByteChannel channel) {
		this.channel = channel;
	}

	public WebSocketWorker getWorkerThread() {
		return workerThread;
	}

	public void setWorkerThread(WebSocketWorker workerThread) {
		this.workerThread = workerThread;
	}


}
