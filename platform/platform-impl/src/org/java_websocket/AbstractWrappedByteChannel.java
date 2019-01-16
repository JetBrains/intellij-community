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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

/**
 * @deprecated
 */
@Deprecated
public class AbstractWrappedByteChannel implements WrappedByteChannel {

	private final ByteChannel channel;

	/**
	 * @deprecated
	 */
	@Deprecated
	public AbstractWrappedByteChannel( ByteChannel towrap ) {
		this.channel = towrap;
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	public AbstractWrappedByteChannel( WrappedByteChannel towrap ) {
		this.channel = towrap;
	}

	@Override
	public int read( ByteBuffer dst ) throws IOException {
		return channel.read( dst );
	}

	@Override
	public boolean isOpen() {
		return channel.isOpen();
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

	@Override
	public int write( ByteBuffer src ) throws IOException {
		return channel.write( src );
	}

	@Override
	public boolean isNeedWrite() {
		return channel instanceof WrappedByteChannel && ((WrappedByteChannel) channel).isNeedWrite();
	}

	@Override
	public void writeMore() throws IOException {
		if( channel instanceof WrappedByteChannel)
			( (WrappedByteChannel) channel ).writeMore();

	}

	@Override
	public boolean isNeedRead() {
		return channel instanceof WrappedByteChannel && ((WrappedByteChannel) channel).isNeedRead();

	}

	@Override
	public int readMore( ByteBuffer dst ) throws IOException {
		return channel instanceof WrappedByteChannel ? ((WrappedByteChannel) channel ).readMore(dst ) : 0;
	}

	@Override
	public boolean isBlocking() {
		if( channel instanceof SocketChannel )
			return ( (SocketChannel) channel ).isBlocking();
		else if( channel instanceof WrappedByteChannel)
			return ( (WrappedByteChannel) channel ).isBlocking();
		return false;
	}

}
