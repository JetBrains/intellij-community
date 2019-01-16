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

import org.java_websocket.util.ByteBufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;


/**
 * A class that represents an SSL/TLS peer, and can be extended to create a client or a server.
 *
 * It makes use of the JSSE framework, and specifically the {@link SSLEngine} logic, which
 * is described by Oracle as "an advanced API, not appropriate for casual use", since
 * it requires the user to implement much of the communication establishment procedure himself.
 * More information about it can be found here: http://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#SSLEngine
 *
 * {@link SSLSocketChannel} implements the handshake protocol, required to establish a connection between two peers,
 * which is common for both client and server and provides the abstract {@link SSLSocketChannel#read(ByteBuffer)} and
 * {@link SSLSocketChannel#write(ByteBuffer)} (String)} methods, that need to be implemented by the specific SSL/TLS peer
 * that is going to extend this class.
 *
 * @author <a href="mailto:alex.a.karnezis@gmail.com">Alex Karnezis</a>
 *         <p>
 *         Modified by marci4 to allow the usage as a ByteChannel
 *         <p>
 *         Permission for usage recieved at May 25, 2017 by Alex Karnezis
 */
public class SSLSocketChannel implements WrappedByteChannel, ByteChannel {

	/**
	 * Logger instance
	 *
	 * @since 1.4.0
	 */
	private static final Logger log = LoggerFactory.getLogger(SSLSocketChannel.class);

	/**
	 * The underlaying socket channel
	 */
	private final SocketChannel socketChannel;

	/**
	 * The engine which will be used for un-/wrapping of buffers
	 */
	private final SSLEngine engine;


	/**
	 * Will contain this peer's application data in plaintext, that will be later encrypted
	 * using {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)} and sent to the other peer. This buffer can typically
	 * be of any size, as long as it is large enough to contain this peer's outgoing messages.
	 * If this peer tries to send a message bigger than buffer's capacity a {@link BufferOverflowException}
	 * will be thrown.
	 */
	private ByteBuffer myAppData;

	/**
	 * Will contain this peer's encrypted data, that will be generated after {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)}
	 * is applied on {@link SSLSocketChannel#myAppData}. It should be initialized using {@link SSLSession#getPacketBufferSize()},
	 * which returns the size up to which, SSL/TLS packets will be generated from the engine under a session.
	 * All SSLEngine network buffers should be sized at least this large to avoid insufficient space problems when performing wrap and unwrap calls.
	 */
	private ByteBuffer myNetData;

	/**
	 * Will contain the other peer's (decrypted) application data. It must be large enough to hold the application data
	 * from any peer. Can be initialized with {@link SSLSession#getApplicationBufferSize()} for an estimation
	 * of the other peer's application data and should be enlarged if this size is not enough.
	 */
	private ByteBuffer peerAppData;

	/**
	 * Will contain the other peer's encrypted data. The SSL/TLS protocols specify that implementations should produce packets containing at most 16 KB of plaintext,
	 * so a buffer sized to this value should normally cause no capacity problems. However, some implementations violate the specification and generate large records up to 32 KB.
	 * If the {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)} detects large inbound packets, the buffer sizes returned by SSLSession will be updated dynamically, so the this peer
	 * should check for overflow conditions and enlarge the buffer using the session's (updated) buffer size.
	 */
	private ByteBuffer peerNetData;

	/**
	 * Will be used to execute tasks that may emerge during handshake in parallel with the server's main thread.
	 */
	private ExecutorService executor;


	public SSLSocketChannel( SocketChannel inputSocketChannel, SSLEngine inputEngine, ExecutorService inputExecutor, SelectionKey key ) throws IOException {
		if( inputSocketChannel == null || inputEngine == null || executor == inputExecutor )
			throw new IllegalArgumentException( "parameter must not be null" );

		this.socketChannel = inputSocketChannel;
		this.engine = inputEngine;
		this.executor = inputExecutor;
		myNetData = ByteBuffer.allocate( engine.getSession().getPacketBufferSize() );
		peerNetData = ByteBuffer.allocate( engine.getSession().getPacketBufferSize() );
		this.engine.beginHandshake();
		if( doHandshake() ) {
			if( key != null ) {
				key.interestOps( key.interestOps() | SelectionKey.OP_WRITE );
			}
		} else {
			try {
				socketChannel.close();
			} catch ( IOException e ) {
				log.error("Exception during the closing of the channel", e);
			}
		}
	}

	@Override
	public synchronized int read( ByteBuffer dst ) throws IOException {
		if( !dst.hasRemaining() ) {
			return 0;
		}
		if( peerAppData.hasRemaining() ) {
			peerAppData.flip();
			return ByteBufferUtils.transferByteBuffer( peerAppData, dst );
		}
		peerNetData.compact();

		int bytesRead = socketChannel.read( peerNetData );
		/*
		 * If bytesRead are 0 put we still have some data in peerNetData still to an unwrap (for testcase 1.1.6)
		 */
		if( bytesRead > 0 || peerNetData.hasRemaining() ) {
			peerNetData.flip();
			while( peerNetData.hasRemaining() ) {
				peerAppData.compact();
				SSLEngineResult result;
				try {
					result = engine.unwrap( peerNetData, peerAppData );
				} catch ( SSLException e ) {
					log.error("SSLExcpetion during unwrap", e);
					throw e;
				}
				switch(result.getStatus()) {
					case OK:
						peerAppData.flip();
						return ByteBufferUtils.transferByteBuffer( peerAppData, dst );
					case BUFFER_UNDERFLOW:
						peerAppData.flip();
						return ByteBufferUtils.transferByteBuffer( peerAppData, dst );
					case BUFFER_OVERFLOW:
						peerAppData = enlargeApplicationBuffer( peerAppData );
						return read(dst);
					case CLOSED:
						closeConnection();
						dst.clear();
						return -1;
					default:
						throw new IllegalStateException( "Invalid SSL status: " + result.getStatus() );
				}
			}
		} else if( bytesRead < 0 ) {
			handleEndOfStream();
		}
		ByteBufferUtils.transferByteBuffer( peerAppData, dst );
		return bytesRead;
	}

	@Override
	public synchronized int write( ByteBuffer output ) throws IOException {
		int num = 0;
		while( output.hasRemaining() ) {
			// The loop has a meaning for (outgoing) messages larger than 16KB.
			// Every wrap call will remove 16KB from the original message and send it to the remote peer.
			myNetData.clear();
			SSLEngineResult result = engine.wrap( output, myNetData );
			switch(result.getStatus()) {
				case OK:
					myNetData.flip();
					while( myNetData.hasRemaining() ) {
						num += socketChannel.write( myNetData );
					}
					break;
				case BUFFER_OVERFLOW:
					myNetData = enlargePacketBuffer( myNetData );
					break;
				case BUFFER_UNDERFLOW:
					throw new SSLException( "Buffer underflow occured after a wrap. I don't think we should ever get here." );
				case CLOSED:
					closeConnection();
					return 0;
				default:
					throw new IllegalStateException( "Invalid SSL status: " + result.getStatus() );
			}
		}
		return num;
	}

	/**
	 * Implements the handshake protocol between two peers, required for the establishment of the SSL/TLS connection.
	 * During the handshake, encryption configuration information - such as the list of available cipher suites - will be exchanged
	 * and if the handshake is successful will lead to an established SSL/TLS session.
	 * <p>
	 * <p/>
	 * A typical handshake will usually contain the following steps:
	 * <p>
	 * <ul>
	 * <li>1. wrap:     ClientHello</li>
	 * <li>2. unwrap:   ServerHello/Cert/ServerHelloDone</li>
	 * <li>3. wrap:     ClientKeyExchange</li>
	 * <li>4. wrap:     ChangeCipherSpec</li>
	 * <li>5. wrap:     Finished</li>
	 * <li>6. unwrap:   ChangeCipherSpec</li>
	 * <li>7. unwrap:   Finished</li>
	 * </ul>
	 * <p/>
	 * Handshake is also used during the end of the session, in order to properly close the connection between the two peers.
	 * A proper connection close will typically include the one peer sending a CLOSE message to another, and then wait for
	 * the other's CLOSE message to close the transport link. The other peer from his perspective would read a CLOSE message
	 * from his peer and then enter the handshake procedure to send his own CLOSE message as well.
	 *
	 * @return True if the connection handshake was successful or false if an error occurred.
	 * @throws IOException - if an error occurs during read/write to the socket channel.
	 */
	private boolean doHandshake() throws IOException {
		SSLEngineResult result;
		HandshakeStatus handshakeStatus;

		// NioSslPeer's fields myAppData and peerAppData are supposed to be large enough to hold all message data the peer
		// will send and expects to receive from the other peer respectively. Since the messages to be exchanged will usually be less
		// than 16KB long the capacity of these fields should also be smaller. Here we initialize these two local buffers
		// to be used for the handshake, while keeping client's buffers at the same size.
		int appBufferSize = engine.getSession().getApplicationBufferSize();
		myAppData = ByteBuffer.allocate( appBufferSize );
		peerAppData = ByteBuffer.allocate( appBufferSize );
		myNetData.clear();
		peerNetData.clear();

		handshakeStatus = engine.getHandshakeStatus();
		boolean handshakeComplete = false;
		while( !handshakeComplete) {
			switch(handshakeStatus) {
				case FINISHED:
					handshakeComplete = !this.peerNetData.hasRemaining();
					if (handshakeComplete)
						return true;
					socketChannel.write(this.peerNetData);
					break;
				case NEED_UNWRAP:
					if( socketChannel.read( peerNetData ) < 0 ) {
						if( engine.isInboundDone() && engine.isOutboundDone() ) {
							return false;
						}
						try {
							engine.closeInbound();
						} catch ( SSLException e ) {
							//Ignore, cant do anything against this exception
						}
						engine.closeOutbound();
						// After closeOutbound the engine will be set to WRAP state, in order to try to send a close message to the client.
						handshakeStatus = engine.getHandshakeStatus();
						break;
					}
					peerNetData.flip();
					try {
						result = engine.unwrap( peerNetData, peerAppData );
						peerNetData.compact();
						handshakeStatus = result.getHandshakeStatus();
					} catch ( SSLException sslException ) {
						engine.closeOutbound();
						handshakeStatus = engine.getHandshakeStatus();
						break;
					}
					switch(result.getStatus()) {
						case OK:
							break;
						case BUFFER_OVERFLOW:
							// Will occur when peerAppData's capacity is smaller than the data derived from peerNetData's unwrap.
							peerAppData = enlargeApplicationBuffer( peerAppData );
							break;
						case BUFFER_UNDERFLOW:
							// Will occur either when no data was read from the peer or when the peerNetData buffer was too small to hold all peer's data.
							peerNetData = handleBufferUnderflow( peerNetData );
							break;
						case CLOSED:
							if( engine.isOutboundDone() ) {
								return false;
							} else {
								engine.closeOutbound();
								handshakeStatus = engine.getHandshakeStatus();
								break;
							}
						default:
							throw new IllegalStateException( "Invalid SSL status: " + result.getStatus() );
					}
					break;
				case NEED_WRAP:
					myNetData.clear();
					try {
						result = engine.wrap( myAppData, myNetData );
						handshakeStatus = result.getHandshakeStatus();
					} catch ( SSLException sslException ) {
						engine.closeOutbound();
						handshakeStatus = engine.getHandshakeStatus();
						break;
					}
					switch(result.getStatus()) {
						case OK:
							myNetData.flip();
							while( myNetData.hasRemaining() ) {
								socketChannel.write( myNetData );
							}
							break;
						case BUFFER_OVERFLOW:
							// Will occur if there is not enough space in myNetData buffer to write all the data that would be generated by the method wrap.
							// Since myNetData is set to session's packet size we should not get to this point because SSLEngine is supposed
							// to produce messages smaller or equal to that, but a general handling would be the following:
							myNetData = enlargePacketBuffer( myNetData );
							break;
						case BUFFER_UNDERFLOW:
							throw new SSLException( "Buffer underflow occured after a wrap. I don't think we should ever get here." );
						case CLOSED:
							try {
								myNetData.flip();
								while( myNetData.hasRemaining() ) {
									socketChannel.write( myNetData );
								}
								// At this point the handshake status will probably be NEED_UNWRAP so we make sure that peerNetData is clear to read.
								peerNetData.clear();
							} catch ( Exception e ) {
								handshakeStatus = engine.getHandshakeStatus();
							}
							break;
						default:
							throw new IllegalStateException( "Invalid SSL status: " + result.getStatus() );
					}
					break;
				case NEED_TASK:
					Runnable task;
					while( ( task = engine.getDelegatedTask() ) != null ) {
						executor.execute( task );
					}
					handshakeStatus = engine.getHandshakeStatus();
					break;

				case NOT_HANDSHAKING:
					break;
				default:
					throw new IllegalStateException( "Invalid SSL status: " + handshakeStatus );
			}
		}

		return true;

	}

	/**
	 * Enlarging a packet buffer (peerNetData or myNetData)
	 *
	 * @param buffer the buffer to enlarge
	 * @return the enlarged buffer
	 */
	private ByteBuffer enlargePacketBuffer( ByteBuffer buffer ) {
		return enlargeBuffer( buffer, engine.getSession().getPacketBufferSize() );
	}

	/**
	 * Enlarging a packet buffer (peerAppData or myAppData)
	 *
	 * @param buffer the buffer to enlarge
	 * @return the enlarged buffer
	 */
	private ByteBuffer enlargeApplicationBuffer( ByteBuffer buffer ) {
		return enlargeBuffer( buffer, engine.getSession().getApplicationBufferSize() );
	}

	/**
	 * Compares <code>sessionProposedCapacity<code> with buffer's capacity. If buffer's capacity is smaller,
	 * returns a buffer with the proposed capacity. If it's equal or larger, returns a buffer
	 * with capacity twice the size of the initial one.
	 *
	 * @param buffer                  - the buffer to be enlarged.
	 * @param sessionProposedCapacity - the minimum size of the new buffer, proposed by {@link SSLSession}.
	 * @return A new buffer with a larger capacity.
	 */
	private ByteBuffer enlargeBuffer( ByteBuffer buffer, int sessionProposedCapacity ) {
		if( sessionProposedCapacity > buffer.capacity() ) {
			buffer = ByteBuffer.allocate( sessionProposedCapacity );
		} else {
			buffer = ByteBuffer.allocate( buffer.capacity() * 2 );
		}
		return buffer;
	}

	/**
	 * Handles {@link SSLEngineResult.Status#BUFFER_UNDERFLOW}. Will check if the buffer is already filled, and if there is no space problem
	 * will return the same buffer, so the client tries to read again. If the buffer is already filled will try to enlarge the buffer either to
	 * session's proposed size or to a larger capacity. A buffer underflow can happen only after an unwrap, so the buffer will always be a
	 * peerNetData buffer.
	 *
	 * @param buffer - will always be peerNetData buffer.
	 * @return The same buffer if there is no space problem or a new buffer with the same data but more space.
	 */
	private ByteBuffer handleBufferUnderflow( ByteBuffer buffer ) {
		if( engine.getSession().getPacketBufferSize() < buffer.limit() ) {
			return buffer;
		} else {
			ByteBuffer replaceBuffer = enlargePacketBuffer( buffer );
			buffer.flip();
			replaceBuffer.put( buffer );
			return replaceBuffer;
		}
	}

	/**
	 * This method should be called when this peer wants to explicitly close the connection
	 * or when a close message has arrived from the other peer, in order to provide an orderly shutdown.
	 * <p/>
	 * It first calls {@link SSLEngine#closeOutbound()} which prepares this peer to send its own close message and
	 * sets {@link SSLEngine} to the <code>NEED_WRAP</code> state. Then, it delegates the exchange of close messages
	 * to the handshake method and finally, it closes socket channel.
	 *
	 * @throws IOException if an I/O error occurs to the socket channel.
	 */
	private void closeConnection() throws IOException {
		engine.closeOutbound();
		try {
			doHandshake();
		} catch ( IOException e ) {
			//Just ignore this exception since we are closing the connection already
		}
		socketChannel.close();
	}

	/**
	 * In addition to orderly shutdowns, an unorderly shutdown may occur, when the transport link (socket channel)
	 * is severed before close messages are exchanged. This may happen by getting an -1 or {@link IOException}
	 * when trying to read from the socket channel, or an {@link IOException} when trying to write to it.
	 * In both cases {@link SSLEngine#closeInbound()} should be called and then try to follow the standard procedure.
	 *
	 * @throws IOException if an I/O error occurs to the socket channel.
	 */
	private void handleEndOfStream() throws IOException {
		try {
			engine.closeInbound();
		} catch ( Exception e ) {
			log.error( "This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream." );
		}
		closeConnection();
	}

	@Override
	public boolean isNeedWrite() {
		return false;
	}

	@Override
	public void writeMore() throws IOException {
		//Nothing to do since we write out all the data in a while loop
	}

	@Override
	public boolean isNeedRead() {
		return peerNetData.hasRemaining() || peerAppData.hasRemaining();
	}

	@Override
	public int readMore( ByteBuffer dst ) throws IOException {
		return read( dst );
	}

	@Override
	public boolean isBlocking() {
		return socketChannel.isBlocking();
	}


	@Override
	public boolean isOpen() {
		return socketChannel.isOpen();
	}

	@Override
	public void close() throws IOException {
		closeConnection();
	}
}