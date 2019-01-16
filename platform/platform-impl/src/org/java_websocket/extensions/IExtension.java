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

package org.java_websocket.extensions;

import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;

/**
 * Interface which specifies all required methods to develop a websocket extension.
 * @since 1.3.5
 */
public interface IExtension {

	/**
	 * Decode a frame with a extension specific algorithm.
	 * The algorithm is subject to be implemented by the specific extension.
	 * The resulting frame will be used in the application
	 *
	 * @param inputFrame the frame, which has do be decoded to be used in the application
	 * @throws InvalidDataException Throw InvalidDataException if the received frame is not correctly implemented by the other endpoint or there are other protocol errors/decoding errors
	 * @since 1.3.5
	 */
	void decodeFrame(Framedata inputFrame) throws InvalidDataException;

	/**
	 * Encode a frame with a extension specific algorithm.
	 * The algorithm is subject to be implemented by the specific extension.
	 * The resulting frame will be send to the other endpoint.
	 *
	 * @param inputFrame the frame, which has do be encoded to be used on the other endpoint
	 * @since 1.3.5
	 */
	void encodeFrame(Framedata inputFrame);

	/**
	 * Check if the received Sec-WebSocket-Extensions header field contains a offer for the specific extension if the endpoint is in the role of a server
	 *
	 * @param inputExtensionHeader the received Sec-WebSocket-Extensions header field offered by the other endpoint
	 * @return true, if the offer does fit to this specific extension
	 * @since 1.3.5
	 */
	boolean acceptProvidedExtensionAsServer(String inputExtensionHeader);

	/**
	 * Check if the received Sec-WebSocket-Extensions header field contains a offer for the specific extension if the endpoint is in the role of a client
	 *
	 * @param inputExtensionHeader the received Sec-WebSocket-Extensions header field offered by the other endpoint
	 * @return true, if the offer does fit to this specific extension
	 * @since 1.3.5
	 */
	boolean acceptProvidedExtensionAsClient(String inputExtensionHeader);

	/**
	 * Check if the received frame is correctly implemented by the other endpoint and there are no specification errors (like wrongly set RSV)
	 *
	 * @param inputFrame the received frame
	 * @throws InvalidDataException Throw InvalidDataException if the received frame is not correctly implementing the specification for the specific extension
	 * @since 1.3.5
	 */
	void isFrameValid(Framedata inputFrame) throws InvalidDataException;

	/**
	 * Return the specific Sec-WebSocket-Extensions header offer for this extension if the endpoint is in the role of a client.
	 * If the extension returns an empty string (""), the offer will not be included in the handshake.
	 *
	 * @return the specific Sec-WebSocket-Extensions header for this extension
	 * @since 1.3.5
	 */
	String getProvidedExtensionAsClient();

	/**
	 * Return the specific Sec-WebSocket-Extensions header offer for this extension if the endpoint is in the role of a server.
	 * If the extension returns an empty string (""), the offer will not be included in the handshake.
	 *
	 * @return the specific Sec-WebSocket-Extensions header for this extension
	 * @since 1.3.5
	 */
	String getProvidedExtensionAsServer();

	/**
	 * Extensions must only be by one websocket at all. To prevent extensions to be used more than once the Websocket implementation should call this method in order to create a new usable version of a given extension instance.<br>
	 * The copy can be safely used in conjunction with a new websocket connection.
	 * @return a copy of the extension
	 * @since 1.3.5
	 */
	IExtension copyInstance();

	/**
	 * Cleaning up internal stats when the draft gets reset.
	 * @since 1.3.5
	 */
	void reset();

	/**
	 * Return a string which should contain the class name as well as additional information about the current configurations for this extension (DEBUG purposes)
	 *
	 * @return a string containing the class name as well as additional information
	 * @since 1.3.5
	 */
	String toString();
}
