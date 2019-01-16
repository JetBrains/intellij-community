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

package org.java_websocket.util;

import java.nio.ByteBuffer;

/**
 * Utility class for ByteBuffers
 */
public class ByteBufferUtils {

	/**
	 * Private constructor for static class
	 */
	private ByteBufferUtils() {
	}

	/**
	 * Transfer from one ByteBuffer to another ByteBuffer
	 *
	 * @param source the ByteBuffer to copy from
	 * @param dest   the ByteBuffer to copy to
	 * @return the number of transferred bytes
	 */
	public static int transferByteBuffer( ByteBuffer source, ByteBuffer dest ) {
		if( source == null || dest == null ) {
			throw new IllegalArgumentException();
		}
		int fremain = source.remaining();
		int toremain = dest.remaining();
		if( fremain > toremain ) {
			int limit = Math.min( fremain, toremain );
			source.limit( limit );
			dest.put( source );
			return limit;
		} else {
			dest.put( source );
			return fremain;
		}
	}

	/**
	 * Get a ByteBuffer with zero capacity
	 *
	 * @return empty ByteBuffer
	 */
	public static ByteBuffer getEmptyByteBuffer() {
		return ByteBuffer.allocate( 0 );
	}
}
