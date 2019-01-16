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

public interface WrappedByteChannel extends ByteChannel {
	/**
	 * returns whether writeMore should be called write additional data.

	 * @return is a additional write needed
	 */
	boolean isNeedWrite();

	/**
	 * Gets called when {@link #isNeedWrite()} ()} requires a additional rite
	 * @throws IOException may be thrown due to an error while writing
	 */
	void writeMore() throws IOException;

	/**
	 * returns whether readMore should be called to fetch data which has been decoded but not yet been returned.
	 * 
	 * @see #read(ByteBuffer)
	 * @see #readMore(ByteBuffer)
	 * @return is a additional read needed
	 **/
	boolean isNeedRead();
	/**
	 * This function does not read data from the underlying channel at all. It is just a way to fetch data which has already be received or decoded but was but was not yet returned to the user.
	 * This could be the case when the decoded data did not fit into the buffer the user passed to {@link #read(ByteBuffer)}.
	 * @param dst the destiny of the read
	 * @return the amount of remaining data
	 * @throws IOException when a error occurred during unwrapping
	 **/
	int readMore(ByteBuffer dst) throws IOException;

	/**
	 * This function returns the blocking state of the channel
	 * @return is the channel blocking
	 */
	boolean isBlocking();
}
