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
 * Sends the expand-modules request. This request expands the modules which
 * have been specified in previous argument requests. The server can assume
 * this is a checkout or export.<br>
 * Expand is not the best word for what this request does. It does not
 * expand a module in any meaningful way. What it does is ask the server
 * to tell you which working directories the server needs to know about in
 * order to handle a checkout of a specific module. This is important where
 * you have aliased modules. If you alias module foo as bar, then you need
 * to know when you do a checkout of foo that bar on disk is an existing
 * checkout of the module.
 * @author  Robert Greig
 */
public final class ExpandModulesRequest extends ResponseExpectingRequest {

	public ExpandModulesRequest() {
		super("expand-modules");
	}
}

