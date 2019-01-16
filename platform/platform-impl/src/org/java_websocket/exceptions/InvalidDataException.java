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

/**
 * exception which indicates that a invalid data was recieved
 */
public class InvalidDataException extends Exception {

    /**
     * Serializable
     */
    private static final long serialVersionUID = 3731842424390998726L;

    /**
     * attribut which closecode will be returned
     */
    private final int closecode;

    /**
     * constructor for a InvalidDataException
     *
     * @param closecode the closecode which will be returned
     */
    public InvalidDataException(int closecode) {
        this.closecode = closecode;
    }

    /**
     * constructor for a InvalidDataException.
     *
     * @param closecode the closecode which will be returned.
     * @param s         the detail message.
     */
    public InvalidDataException(int closecode, String s) {
        super(s);
        this.closecode = closecode;
    }

    /**
     * constructor for a InvalidDataException.
     *
     * @param closecode the closecode which will be returned.
     * @param t         the throwable causing this exception.
     */
    public InvalidDataException(int closecode, Throwable t) {
        super(t);
        this.closecode = closecode;
    }

    /**
     * constructor for a InvalidDataException.
     *
     * @param closecode the closecode which will be returned.
     * @param s         the detail message.
     * @param t         the throwable causing this exception.
     */
    public InvalidDataException(int closecode, String s, Throwable t) {
        super(s, t);
        this.closecode = closecode;
    }

    /**
     * Getter closecode
     *
     * @return the closecode
     */
    public int getCloseCode() {
        return closecode;
    }

}
