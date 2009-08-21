/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/

 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.

 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.connection;

import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides a method for accessing a connection, in order to be able to
 * communicate using the CVS Protocol. Instances of this interface are used
 * by the Client class to communicate with the server without being too
 * concerned with how the communication is taking place or how it was
 * set up.
 * @author  Robert Greig
 */
public interface IConnection extends Closeable {

	InputStream getInputStream();

	OutputStream getOutputStream();

	String getRepository();

	void verify(IStreamLogger streamLogger) throws AuthenticationException;

	/**
	 * Open a connection with the server. Until this method is called, no
	 * communication with the server can take place. This Client will
	 * call this method before interacting with the server. It is up to
	 * implementing classes to ensure that they are configured to
	 * talk to the server (e.g. port number etc.)
	 * @throws AuthenticationException if the connection with the server
	 * cannot be established
	 */
	void open(IStreamLogger streamLogger) throws AuthenticationException;
}
