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
package org.netbeans.lib.cvsclient.request;

import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * The directory request. Tell the server which directory to use.
 * @author  Robert Greig
 */
public final class LocalDirectoryRequest extends AbstractRequest {

	// Fields =================================================================

	private final String repository;

	// Setup ==================================================================

	/**
	 * Create a new DirectoryRequest
	 * @param localDirectory the local directory argument
	 * @param repository the repository argument
	 */
	public LocalDirectoryRequest(String repository) {
		BugLog.getInstance().assertNotNull(repository);

		this.repository = repository;
	}

	// Implemented ============================================================

	/**
	 * Get the request String that will be passed to the server
	 * @return the request String
	 */
	@Override
        public String getRequestString() {
		return "Directory .\n" + repository + "\n";
	}
}
