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
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidFrameException;

/**
 * Abstract class to represent control frames
 */
public abstract class ControlFrame extends FramedataImpl1 {

	/**
	 * Class to represent a control frame
	 * @param opcode the opcode to use
	 */
	public ControlFrame( Opcode opcode ) {
		super( opcode );
	}

	@Override
	public void isValid() throws InvalidDataException {
		if( !isFin() ) {
			throw new InvalidFrameException( "Control frame cant have fin==false set" );
		}
		if( isRSV1() ) {
			throw new InvalidFrameException( "Control frame cant have rsv1==true set" );
		}
		if( isRSV2() ) {
			throw new InvalidFrameException( "Control frame cant have rsv2==true set" );
		}
		if( isRSV3() ) {
			throw new InvalidFrameException( "Control frame cant have rsv3==true set" );
		}
	}
}
