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

package org.java_websocket.drafts;

import org.java_websocket.WebSocketImpl;
import org.java_websocket.enums.CloseHandshakeType;
import org.java_websocket.enums.HandshakeState;
import org.java_websocket.enums.Opcode;
import org.java_websocket.enums.Role;
import org.java_websocket.exceptions.IncompleteHandshakeException;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.framing.*;
import org.java_websocket.handshake.*;
import org.java_websocket.util.Charsetfunctions;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Base class for everything of a websocket specification which is not common such as the way the handshake is read or frames are transfered.
 **/
public abstract class Draft {

	/** In some cases the handshake will be parsed different depending on whether */
	protected Role role = null;

	protected Opcode continuousFrameType = null;

	public static ByteBuffer readLine( ByteBuffer buf ) {
		ByteBuffer sbuf = ByteBuffer.allocate( buf.remaining() );
		byte prev;
		byte cur = '0';
		while ( buf.hasRemaining() ) {
			prev = cur;
			cur = buf.get();
			sbuf.put( cur );
			if( prev == (byte) '\r' && cur == (byte) '\n' ) {
				sbuf.limit( sbuf.position() - 2 );
				sbuf.position( 0 );
				return sbuf;

			}
		}
		// ensure that there wont be any bytes skipped
		buf.position( buf.position() - sbuf.position() );
		return null;
	}

	public static String readStringLine( ByteBuffer buf ) {
		ByteBuffer b = readLine( buf );
		return b == null ? null : Charsetfunctions.stringAscii(b.array(), 0, b.limit() );
	}

	public static HandshakeBuilder translateHandshakeHttp( ByteBuffer buf, Role role ) throws InvalidHandshakeException {
		HandshakeBuilder handshake;

		String line = readStringLine( buf );
		if( line == null )
			throw new IncompleteHandshakeException( buf.capacity() + 128 );

		String[] firstLineTokens = line.split( " ", 3 );// eg. HTTP/1.1 101 Switching the Protocols
		if( firstLineTokens.length != 3 ) {
			throw new InvalidHandshakeException();
		}
		if( role == Role.CLIENT ) {
			handshake = translateHandshakeHttpClient(firstLineTokens, line);
		} else {
			handshake = translateHandshakeHttpServer(firstLineTokens, line);
		}
		line = readStringLine( buf );
		while ( line != null && line.length() > 0 ) {
			String[] pair = line.split( ":", 2 );
			if( pair.length != 2 )
				throw new InvalidHandshakeException( "not an http header" );
			// If the handshake contains already a specific key, append the new value
			if ( handshake.hasFieldValue( pair[ 0 ] ) ) {
				handshake.put( pair[0], handshake.getFieldValue( pair[ 0 ] ) + "; " + pair[1].replaceFirst( "^ +", "" ) );
			} else {
				handshake.put( pair[0], pair[1].replaceFirst( "^ +", "" ) );
			}
			line = readStringLine( buf );
		}
		if( line == null )
			throw new IncompleteHandshakeException();
		return handshake;
	}

	/**
	 * Checking the handshake for the role as server
	 * @return a handshake
	 * @param firstLineTokens the token of the first line split as as an string array
	 * @param line the whole line
	 */
	private static HandshakeBuilder translateHandshakeHttpServer(String[] firstLineTokens, String line) throws InvalidHandshakeException {
		// translating/parsing the request from the CLIENT
		if (!"GET".equalsIgnoreCase(firstLineTokens[0])) {
			throw new InvalidHandshakeException( String.format("Invalid request method received: %s Status line: %s", firstLineTokens[0],line));
		}
		if (!"HTTP/1.1".equalsIgnoreCase(firstLineTokens[2])) {
			throw new InvalidHandshakeException( String.format("Invalid status line received: %s Status line: %s", firstLineTokens[2], line));
		}
		ClientHandshakeBuilder clienthandshake = new HandshakeImpl1Client();
		clienthandshake.setResourceDescriptor( firstLineTokens[ 1 ] );
		return clienthandshake;
	}

	/**
	 * Checking the handshake for the role as client
	 * @return a handshake
	 * @param firstLineTokens the token of the first line split as as an string array
	 * @param line the whole line
	 */
	private static HandshakeBuilder translateHandshakeHttpClient(String[] firstLineTokens, String line) throws InvalidHandshakeException {
		// translating/parsing the response from the SERVER
		if (!"101".equals(firstLineTokens[1])) {
			throw new InvalidHandshakeException( String.format("Invalid status code received: %s Status line: %s", firstLineTokens[1], line));
		}
		if (!"HTTP/1.1".equalsIgnoreCase(firstLineTokens[0])) {
			throw new InvalidHandshakeException( String.format("Invalid status line received: %s Status line: %s", firstLineTokens[0], line));
		}
		HandshakeBuilder handshake = new HandshakeImpl1Server();
		ServerHandshakeBuilder serverhandshake = (ServerHandshakeBuilder) handshake;
		serverhandshake.setHttpStatus( Short.parseShort( firstLineTokens[ 1 ] ) );
		serverhandshake.setHttpStatusMessage( firstLineTokens[ 2 ] );
		return handshake;
	}

	public abstract HandshakeState acceptHandshakeAsClient(ClientHandshake request, ServerHandshake response ) throws InvalidHandshakeException;

	public abstract HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata ) throws InvalidHandshakeException;

	protected boolean basicAccept( Handshakedata handshakedata ) {
		return handshakedata.getFieldValue( "Upgrade" ).equalsIgnoreCase( "websocket" ) && handshakedata.getFieldValue( "Connection" ).toLowerCase( Locale.ENGLISH ).contains( "upgrade" );
	}

	public abstract ByteBuffer createBinaryFrame( Framedata framedata );

	public abstract List<Framedata> createFrames(ByteBuffer binary, boolean mask );

	public abstract List<Framedata> createFrames(String text, boolean mask );


	/**
	 * Handle the frame specific to the draft
	 * @param webSocketImpl the websocketimpl used for this draft
	 * @param frame the frame which is supposed to be handled
	 * @throws InvalidDataException will be thrown on invalid data
	 */
	public abstract void processFrame(WebSocketImpl webSocketImpl, Framedata frame ) throws InvalidDataException;

	public List<Framedata> continuousFrame(Opcode op, ByteBuffer buffer, boolean fin ) {
		if(op != Opcode.BINARY && op != Opcode.TEXT) {
			throw new IllegalArgumentException( "Only Opcode.BINARY or  Opcode.TEXT are allowed" );
		}
		DataFrame bui = null;
		if( continuousFrameType != null ) {
			bui = new ContinuousFrame();
		} else {
			continuousFrameType = op;
			if (op == Opcode.BINARY) {
				bui = new BinaryFrame();
			} else if (op == Opcode.TEXT) {
				bui = new TextFrame();
			}
		}
		bui.setPayload( buffer );
		bui.setFin( fin );
		try {
			bui.isValid();
		} catch ( InvalidDataException e ) {
			throw new IllegalArgumentException( e ); // can only happen when one builds close frames(Opcode.Close)
		}
		if( fin ) {
			continuousFrameType = null;
		} else {
			continuousFrameType = op;
		}
		return Collections.singletonList( (Framedata) bui );
	}

	public abstract void reset();

	/**
	 * @deprecated use createHandshake without the role
	 */
	@Deprecated
	public List<ByteBuffer> createHandshake(Handshakedata handshakedata, Role ownrole ) {
		return createHandshake(handshakedata);
	}

	public List<ByteBuffer> createHandshake( Handshakedata handshakedata) {
		return createHandshake( handshakedata, true );
	}

	/**
	 * @deprecated use createHandshake without the role since it does not have any effect
	 */
	@Deprecated
	public List<ByteBuffer> createHandshake(Handshakedata handshakedata, Role ownrole, boolean withcontent ) {
		return createHandshake(handshakedata, withcontent);
	}

	public List<ByteBuffer> createHandshake(Handshakedata handshakedata, boolean withcontent ) {
		StringBuilder bui = new StringBuilder( 100 );
		if( handshakedata instanceof ClientHandshake ) {
			bui.append( "GET " ).append( ( (ClientHandshake) handshakedata ).getResourceDescriptor() ).append( " HTTP/1.1" );
		} else if( handshakedata instanceof ServerHandshake) {
			bui.append("HTTP/1.1 101 ").append(((ServerHandshake) handshakedata).getHttpStatusMessage());
		} else {
			throw new IllegalArgumentException( "unknown role" );
		}
		bui.append( "\r\n" );
		Iterator<String> it = handshakedata.iterateHttpFields();
		while ( it.hasNext() ) {
			String fieldname = it.next();
			String fieldvalue = handshakedata.getFieldValue( fieldname );
			bui.append( fieldname );
			bui.append( ": " );
			bui.append( fieldvalue );
			bui.append( "\r\n" );
		}
		bui.append( "\r\n" );
		byte[] httpheader = Charsetfunctions.asciiBytes(bui.toString() );

		byte[] content = withcontent ? handshakedata.getContent() : null;
		ByteBuffer bytebuffer = ByteBuffer.allocate( ( content == null ? 0 : content.length ) + httpheader.length );
		bytebuffer.put( httpheader );
		if( content != null ) {
			bytebuffer.put(content);
		}
		bytebuffer.flip();
		return Collections.singletonList( bytebuffer );
	}

	public abstract ClientHandshakeBuilder postProcessHandshakeRequestAsClient(ClientHandshakeBuilder request ) throws InvalidHandshakeException;

	public abstract HandshakeBuilder postProcessHandshakeResponseAsServer( ClientHandshake request, ServerHandshakeBuilder response ) throws InvalidHandshakeException;

	public abstract List<Framedata> translateFrame(ByteBuffer buffer ) throws InvalidDataException;

	public abstract CloseHandshakeType getCloseHandshakeType();

	/**
	 * Drafts must only be by one websocket at all. To prevent drafts to be used more than once the Websocket implementation should call this method in order to create a new usable version of a given draft instance.<br>
	 * The copy can be safely used in conjunction with a new websocket connection.
	 * @return a copy of the draft
	 */
	public abstract Draft copyInstance();

	public Handshakedata translateHandshake(ByteBuffer buf ) throws InvalidHandshakeException {
		return translateHandshakeHttp( buf, role );
	}

	public int checkAlloc( int bytecount ) throws InvalidDataException {
		if( bytecount < 0 )
			throw new InvalidDataException(CloseFrame.PROTOCOL_ERROR, "Negative count" );
		return bytecount;
	}

	int readVersion( Handshakedata handshakedata ) {
		String vers = handshakedata.getFieldValue( "Sec-WebSocket-Version" );
		if( vers.length() > 0 ) {
			int v;
			try {
				v = new Integer( vers.trim() );
				return v;
			} catch ( NumberFormatException e ) {
				return -1;
			}
		}
		return -1;
	}

	public void setParseMode( Role role ) {
		this.role = role;
	}
	
	public Role getRole() {
		return role;
	}

	public String toString() {
		return getClass().getSimpleName();
	}

}
