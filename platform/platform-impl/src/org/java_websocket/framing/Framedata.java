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

package org.java_websocket.framing;

import org.java_websocket.enums.Opcode;

import java.nio.ByteBuffer;

/**
 * The interface for the frame
 */
public interface Framedata {

	/**
	 * Indicates that this is the final fragment in a message.  The first fragment MAY also be the final fragment.
	 * @return true, if this frame is the final fragment
	 */
	boolean isFin();

	/**
	 * Indicates that this frame has the rsv1 bit set.
	 * @return true, if this frame has the rsv1 bit set
	 */
	boolean isRSV1();

	/**
	 * Indicates that this frame has the rsv2 bit set.
	 * @return true, if this frame has the rsv2 bit set
	 */
	boolean isRSV2();

	/**
	 * Indicates that this frame has the rsv3 bit set.
	 * @return true, if this frame has the rsv3 bit set
	 */
	boolean isRSV3();

	/**
	 * Defines whether the "Payload data" is masked.
	 * @return true, "Payload data" is masked
	 */
	boolean getTransfereMasked();

	/**
	 * Defines the interpretation of the "Payload data".
	 * @return the interpretation as a Opcode
	 */
	Opcode getOpcode();

	/**
	 * The "Payload data" which was sent in this frame
	 * @return the "Payload data" as ByteBuffer
	 */
	ByteBuffer getPayloadData();// TODO the separation of the application data and the extension data is yet to be done

	/**
	 * Appends an additional frame to the current frame
	 *
	 * This methods does not override the opcode, but does override the fin
	 * @param nextframe the additional frame
	 */
	void append(Framedata nextframe);
}
