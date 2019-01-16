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
import org.java_websocket.exceptions.InvalidFrameException;
import org.java_websocket.framing.Framedata;

/**
 * Class which represents the normal websocket implementation specified by rfc6455.
 *
 * This is a fallback and will always be available for a Draft_6455
 *
 * @since 1.3.5
 */
public class DefaultExtension implements IExtension {

	@Override
	public void decodeFrame( Framedata inputFrame ) throws InvalidDataException {
		//Nothing to do here
	}

	@Override
	public void encodeFrame( Framedata inputFrame ) {
		//Nothing to do here
	}

	@Override
	public boolean acceptProvidedExtensionAsServer( String inputExtension ) {
		return true;
	}

	@Override
	public boolean acceptProvidedExtensionAsClient( String inputExtension ) {
		return true;
	}

	@Override
	public void isFrameValid( Framedata inputFrame ) throws InvalidDataException {
		if( inputFrame.isRSV1() || inputFrame.isRSV2() || inputFrame.isRSV3() ) {
			throw new InvalidFrameException( "bad rsv RSV1: " + inputFrame.isRSV1() + " RSV2: " + inputFrame.isRSV2() + " RSV3: " + inputFrame.isRSV3() );
		}
	}

	@Override
	public String getProvidedExtensionAsClient() {
		return "";
	}

	@Override
	public String getProvidedExtensionAsServer() {
		return "";
	}

	@Override
	public IExtension copyInstance() {
		return new DefaultExtension();
	}

	public void reset() {
		//Nothing to do here. No internal stats.
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals( Object o ) {
		return this == o || o != null && getClass() == o.getClass();
	}
}
