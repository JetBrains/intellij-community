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
package org.netbeans.lib.cvsclient.command;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * This exception is thrown when an error occurs while executing a command.
 * It is nearly always a container for another exception.
 * @author  Robert Greig
 */
public class CommandException extends Exception {

	// Fields =================================================================

	private Exception underlyingException;
	private String localizedMessage;
	private String message;

	// Setup ==================================================================

	public CommandException(Exception underlyingException, String localizedMessage) {
		this.underlyingException = underlyingException;
		this.localizedMessage = localizedMessage;
	}

	protected CommandException(Exception underlyingException) {
		super(underlyingException.getLocalizedMessage());
		this.underlyingException = underlyingException;
	}

	protected CommandException(String message) {
		super(message);
		this.message = message;
		this.localizedMessage = message;
	}

	// Implemented ============================================================

	public final Exception getUnderlyingException() {
		return underlyingException;
	}

	@Override
        public final void printStackTrace() {
		if (underlyingException != null) {
			underlyingException.printStackTrace();
		}
		else {
			super.printStackTrace();
		}
	}

	@Override
        public final void printStackTrace(PrintStream stream) {
		if (underlyingException != null) {
			underlyingException.printStackTrace(stream);
		}
		else {
			super.printStackTrace(stream);
		}
	}

	@Override
        public final void printStackTrace(PrintWriter writer) {
		if (underlyingException != null) {
			underlyingException.printStackTrace(writer);
		}
		else {
			super.printStackTrace(writer);
		}
	}

	@Override
        public final String getLocalizedMessage() {
		if (localizedMessage != null) return localizedMessage;
		if (message != null) return message;
		return super.getMessage();
	}

	@Override
        public final String getMessage() {
		return message;
	}
}
