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

package org.java_websocket.handshake;

import java.util.Collections;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Implementation of a handshake builder
 */
public class HandshakedataImpl1 implements HandshakeBuilder {

	/**
	 * Attribute for the content of the handshake
	 */
	private byte[] content;

	/**
	 * Attribute for the http fields and values
	 */
	private TreeMap<String,String> map;

	/**
	 * Constructor for handshake implementation
	 */
	public HandshakedataImpl1() {
		map = new TreeMap<String,String>( String.CASE_INSENSITIVE_ORDER );
	}

	@Override
	public Iterator<String> iterateHttpFields() {
		return Collections.unmodifiableSet( map.keySet() ).iterator();// Safety first
	}

	@Override
	public String getFieldValue( String name ) {
		String s = map.get( name );
		if ( s == null ) {
			return "";
		}
		return s;
	}

	@Override
	public byte[] getContent() {
		return content;
	}

	@Override
	public void setContent( byte[] content ) {
		this.content = content;
	}

	@Override
	public void put( String name, String value ) {
		map.put( name, value );
	}

	@Override
	public boolean hasFieldValue( String name ) {
		return map.containsKey( name );
	}
}
