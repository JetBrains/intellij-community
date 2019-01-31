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
package org.netbeans.lib.cvsclient.request;

import org.netbeans.lib.cvsclient.file.FileObject;

/**
 * @author  Thomas Singer
 */
public final class IsModifiedRequest extends AbstractFileStateRequest {

	// Setup ==================================================================

	public IsModifiedRequest(FileObject fileObject) {
		super(fileObject);
	}

	// Implemented ============================================================

	@Override
        public String getRequestString() {
		return "Is-modified " + getFileName() + "\n";
	}
}
