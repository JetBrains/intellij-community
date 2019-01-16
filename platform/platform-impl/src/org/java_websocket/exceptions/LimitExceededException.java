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
 * exception which indicates that the message limited was exceeded (CloseFrame.TOOBIG)
 */
public class LimitExceededException extends InvalidDataException {

    /**
     * Serializable
     */
    private static final long serialVersionUID = 6908339749836826785L;

    /**
     * A closer indication about the limit
     */
    private final int limit;

    /**
     * constructor for a LimitExceededException
     * <p>
     * calling LimitExceededException with closecode TOOBIG
     */
    public LimitExceededException() {
        this(Integer.MAX_VALUE);
    }

    /**
     * constructor for a LimitExceededException
     * <p>
     * calling InvalidDataException with closecode TOOBIG
     */
    public LimitExceededException(int limit) {
        super( CloseFrame.TOOBIG);
        this.limit = limit;
    }

    /**
     * constructor for a LimitExceededException
     * <p>
     * calling InvalidDataException with closecode TOOBIG
     */
    public LimitExceededException(String s, int limit) {
        super( CloseFrame.TOOBIG, s);
        this.limit = limit;
    }

    /**
     * constructor for a LimitExceededException
     * <p>
     * calling InvalidDataException with closecode TOOBIG
     *
     * @param s the detail message.
     */
    public LimitExceededException(String s) {
        this(s, Integer.MAX_VALUE);
    }

    /**
     * Get the limit which was hit so this exception was caused
     * @return the limit as int
     */
    public int getLimit() {
        return limit;
    }
}
