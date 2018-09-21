/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.command.reservedcheckout;

import org.netbeans.lib.cvsclient.command.AbstractMessageParser;

/**
 * @author  Thomas Singer
 */
final class EditEditorsMessageParser extends AbstractMessageParser {

	// Fields =================================================================

	private final String allowedUser;
	private boolean filesEdited;

	// Setup ==================================================================

	EditEditorsMessageParser(String allowedUser) {
		this.allowedUser = allowedUser;
	}

	// Implemented ============================================================

	@Override
        public void parseLine(String line, boolean isErrorMessage) {
		if (isErrorMessage) {
			return;
		}

		final TabStringTokenizer tokenizer = new TabStringTokenizer(line);
		tokenizer.nextToken();
		final String user = tokenizer.nextToken();
		tokenizer.nextToken();
		tokenizer.nextToken();
		if (tokenizer.nextToken() == null) {
			return;
		}

		if (user.equals(allowedUser)) {
			return;
		}

		filesEdited = true;
	}

  @Override
  public void outputDone() {
	}

	// Accessing ==============================================================

	public boolean isFilesEdited() {
		return filesEdited;
	}
}
