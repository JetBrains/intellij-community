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

/**
 * Sends a request telling the server which responses this client understands.
 * This request should be sent before any commands are executed. This is done
 * automatically by the Client class.
 * @author  Robert Greig
 */
public final class ValidResponsesRequest extends AbstractRequest {

	// Implemented ============================================================

	/**
	 * Get the request String that will be passed to the server
	 * @return the request String
	 */
	public String getRequestString() {
		return "Valid-responses E M MT Updated Checked-in ok error "
		        + "Clear-static-directory Valid-requests Merged Removed "
		        + "Copy-file Mod-time Mode Kopt Template Set-static-directory "
		        + "Module-expansion Clear-sticky Set-sticky New-entry Mbinary EntriesExtra\n";
	}
}
