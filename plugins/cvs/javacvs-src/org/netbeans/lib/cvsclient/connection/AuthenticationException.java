/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.connection;

/**
 * This exception is thrown when a connection with the server cannot be made,
 * for whatever reason.
 * It may be that the username and/or password are incorrect or it could be
 * that the port number is incorrect. Note that authentication is not
 * restricted here to mean security.
 * @author  Robert Greig
 */
public class AuthenticationException extends Exception {

	// Setup ==================================================================

	protected AuthenticationException(String message) {
		super(message);
	}

	public AuthenticationException(String message, Throwable cause) {
		super(message, cause);
	}

        public boolean isSolveable() {
          return false;
        }
}
