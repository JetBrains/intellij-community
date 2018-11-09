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

import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * Sends an entry to the server, to tell the server which version of a file
 * is on the local machine. The filename is relative to the most recent
 * Directory request. Note that if an <pre>Entry</pre> request is sent
 * without <pre>Modified</pre>, <pre>Is-Modified</pre> or <pre>Unchanged</pre>
 * it means that the file is lost. Also note that if <pre>Modified</pre>,
 * <pre>Is-Modified</pre> or </pre>Unchanged</pre> is sent with <pre>Entry
 * </pre> then </pre>Entry</pre> must be sent first.
 * @author  Robert Greig
 */
public final class EntryRequest extends AbstractRequest {

	// Fields =================================================================

	private final Entry entry;

	// Setup ==================================================================

	public EntryRequest(Entry entry) {
		BugLog.getInstance().assertNotNull(entry);

		this.entry = entry;
	}

	// Implemented ============================================================

	@Override
        public String getRequestString() {
		return "Entry " + entry.toString() + "\n";
	}
}