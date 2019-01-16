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

import org.java_websocket.enums.Role;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

public class SocketChannelIOHelper {

	public static boolean read(final ByteBuffer buf, WebSocketImpl ws, ByteChannel channel ) throws IOException {
		buf.clear();
		int read = channel.read( buf );
		buf.flip();

		if( read == -1 ) {
			ws.eot();
			return false;
		}
		return read != 0;
	}

	/**
	 * @see WrappedByteChannel#readMore(ByteBuffer)
	 * @param buf The ByteBuffer to read from
	 * @param ws The WebSocketImpl associated with the channels
	 * @param channel The channel to read from
	 * @return returns Whether there is more data left which can be obtained via {@link WrappedByteChannel#readMore(ByteBuffer)}
	 * @throws IOException May be thrown by {@link WrappedByteChannel#readMore(ByteBuffer)}#
	 **/
	public static boolean readMore(final ByteBuffer buf, WebSocketImpl ws, WrappedByteChannel channel ) throws IOException {
		buf.clear();
		int read = channel.readMore( buf );
		buf.flip();

		if( read == -1 ) {
			ws.eot();
			return false;
		}
		return channel.isNeedRead();
	}

	/** Returns whether the whole outQueue has been flushed
	 * @param ws The WebSocketImpl associated with the channels
	 * @param sockchannel The channel to write to
	 * @throws IOException May be thrown by {@link WrappedByteChannel#writeMore()}
	 * @return returns Whether there is more data to write
	 */
	public static boolean batch(WebSocketImpl ws, ByteChannel sockchannel ) throws IOException {
		if (ws == null) {
			return false;
		}
		ByteBuffer buffer = ws.outQueue.peek();
		WrappedByteChannel c = null;

		if( buffer == null ) {
			if( sockchannel instanceof WrappedByteChannel) {
				c = (WrappedByteChannel) sockchannel;
				if( c.isNeedWrite() ) {
					c.writeMore();
				}
			}
		} else {
			do {// FIXME writing as much as possible is unfair!!
				/*int written = */sockchannel.write( buffer );
				if( buffer.remaining() > 0 ) {
					return false;
				} else {
					ws.outQueue.poll(); // Buffer finished. Remove it.
					buffer = ws.outQueue.peek();
				}
			} while ( buffer != null );
		}

		if( ws.outQueue.isEmpty() && ws.isFlushAndClose() && ws.getDraft() != null && ws.getDraft().getRole() != null && ws.getDraft().getRole() == Role.SERVER ) {//
			ws.closeConnection();
		}
		return c == null || !((WrappedByteChannel) sockchannel).isNeedWrite();
	}
}
