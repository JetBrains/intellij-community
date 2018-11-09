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
package org.netbeans.lib.cvsclient.request;

/**
 * Implements the Sticky request
 * @author  Milos Kleint
 */
public final class StickyRequest extends AbstractRequest {

	// Fields =================================================================

	private final String sticky;

	// Setup ==================================================================

	public StickyRequest(String sticky) {
		this.sticky = sticky;
	}

	/**
	 * Get the request String that will be passed to the server
	 * @return the request String
	 */
	@Override
        public String getRequestString() {
		return "Sticky " + sticky + "\n";
	}
}