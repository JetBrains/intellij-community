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

package org.java_websocket.exceptions;

import org.java_websocket.framing.CloseFrame;

/**
 * exception which indicates that a invalid handshake was recieved (CloseFrame.PROTOCOL_ERROR)
 */
public class InvalidHandshakeException extends InvalidDataException {

    /**
     * Serializable
     */
    private static final long serialVersionUID = -1426533877490484964L;

    /**
     * constructor for a InvalidHandshakeException
     * <p>
     * calling InvalidDataException with closecode PROTOCOL_ERROR
     */
    public InvalidHandshakeException() {
        super(CloseFrame.PROTOCOL_ERROR);
    }

    /**
     * constructor for a InvalidHandshakeException
     * <p>
     * calling InvalidDataException with closecode PROTOCOL_ERROR
     *
     * @param s the detail message.
     * @param t the throwable causing this exception.
     */
    public InvalidHandshakeException(String s, Throwable t) {
        super(CloseFrame.PROTOCOL_ERROR, s, t);
    }

    /**
     * constructor for a InvalidHandshakeException
     * <p>
     * calling InvalidDataException with closecode PROTOCOL_ERROR
     *
     * @param s the detail message.
     */
    public InvalidHandshakeException(String s) {
        super(CloseFrame.PROTOCOL_ERROR, s);
    }

    /**
     * constructor for a InvalidHandshakeException
     * <p>
     * calling InvalidDataException with closecode PROTOCOL_ERROR
     *
     * @param t the throwable causing this exception.
     */
    public InvalidHandshakeException(Throwable t) {
        super(CloseFrame.PROTOCOL_ERROR, t);
    }

}
