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
package org.netbeans.lib.cvsclient.util;

import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.util.LinkedList;
import java.util.List;

/**
 * @author   Milos Kleint, Thomas Singer
 */
public final class DefaultIgnoreFileFilter
        implements IIgnoreFileFilter {

	// Fields =================================================================

	private final List<IStringPattern> patterns = new LinkedList<>();

	// Setup ==================================================================

	public DefaultIgnoreFileFilter() {
	}

	// Accessing ==============================================================

	/**
	 * Adds a string to the list of ignore file patters using the SimpleStringPattern.
	 */
	public void addPattern(String pattern) {
		if (pattern.equals("!")) {
			clearPatterns();
		}
		else {
			patterns.add(new SimpleStringPattern(pattern));
		}
	}

	/**
	 * Clears the list of patters.
	 * To be used when the "!" character is used in any of the .cvsignore lists.
	 */
	private void clearPatterns() {
		patterns.clear();
	}

	// Implemented ============================================================

	/**
	 * A file is checked against the patterns in the filter.
	 * If any of these matches, the file should be ignored.
	 */
	@Override
        public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
		final String noneCvsFile = abstractFileObject.getName();
		// current implementation ignores the directory parameter.
		// in future or different implementations can add the directory dependant .cvsignore lists
		for (IStringPattern pattern : patterns) {
			if (pattern.doesMatch(noneCvsFile)) {
				return true;
			}
		}
		return false;
	}

}
