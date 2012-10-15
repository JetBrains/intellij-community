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
package org.netbeans.lib.cvsclient.command.log;

/**
 * @author  Thomas Singer
 */
public final class SymbolicName {

	// Fields =================================================================

	private String name;
	private String revision;

	// Setup ==================================================================

	public SymbolicName() {
	}

	// Accessing ==============================================================

	public String getName() {
		return name;
	}

	public void setName(String symName) {
		name = symName.intern();
	}

	public void setRevision(String rev) {
		revision = rev.intern();
	}

	public String getRevision() {
		return revision;
	}
}
