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

package org.java_websocket.protocols;

import java.util.regex.Pattern;

/**
 * Class which represents the protocol used as Sec-WebSocket-Protocol
 *
 * @since 1.3.7
 */
public class Protocol implements IProtocol {

	private static final Pattern patternSpace = Pattern.compile(" ");
	private static final Pattern patternComma = Pattern.compile(",");

	/**
	 * Attribute for the provided protocol
	 */
	private final String providedProtocol;

	/**
	 * Constructor for a Sec-Websocket-Protocol
	 *
	 * @param providedProtocol the protocol string
	 */
	public Protocol( String providedProtocol ) {
		if( providedProtocol == null ) {
			throw new IllegalArgumentException();
		}
		this.providedProtocol = providedProtocol;
	}

	@Override
	public boolean acceptProvidedProtocol( String inputProtocolHeader ) {
		String protocolHeader = patternSpace.matcher(inputProtocolHeader).replaceAll("");
		String[] headers = patternComma.split(protocolHeader);
		for( String header : headers ) {
			if( providedProtocol.equals( header ) ) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getProvidedProtocol() {
		return this.providedProtocol;
	}

	@Override
	public IProtocol copyInstance() {
		return new Protocol(getProvidedProtocol() );
	}

	@Override
	public String toString() {
		return getProvidedProtocol();
	}

	@Override
	public boolean equals( Object o ) {
		if( this == o ) return true;
		if( o == null || getClass() != o.getClass() ) return false;

		Protocol protocol = (Protocol) o;

		return providedProtocol.equals( protocol.providedProtocol );
	}

	@Override
	public int hashCode() {
		return providedProtocol.hashCode();
	}
}
