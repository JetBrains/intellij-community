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
 * Exception which indicates that the frame is not yet complete
 */
public class IncompleteException extends Exception {

	/**
	 * It's Serializable.
	 */
	private static final long serialVersionUID = 7330519489840500997L;

	/**
	 * The preferred size
	 */
	private final int preferredSize;

	/**
	 * Constructor for the preferred size of a frame
	 * @param preferredSize the preferred size of a frame
	 */
	public IncompleteException( int preferredSize ) {
		this.preferredSize = preferredSize;
	}

	/**
	 * Getter for the preferredSize
	 * @return the value of the preferred size
	 */
	public int getPreferredSize() {
		return preferredSize;
	}
}
