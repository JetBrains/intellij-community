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
package org.netbeans.lib.cvsclient.file;

/**
 * Thiz represents a relative file name relative to an root entry (normally directory).
 *
 * @author  Thomas Singer
 */
public final class DirectoryObject extends AbstractFileObject {

	// Constants ==============================================================

	private static final DirectoryObject ROOT = new DirectoryObject("/");

	// Static =================================================================

	public static DirectoryObject getRoot() {
		return ROOT;
	}

	public static DirectoryObject createInstance(String relativeFileName) {
		return new DirectoryObject(relativeFileName);
	}

	public static DirectoryObject createInstance(DirectoryObject directoryObject, String directoryPathRelativeToDirectoryObject) {
		final String relativeFileName = FileUtils.ensureTrailingSlash(directoryObject.getPath())
		        + FileUtils.removeLeadingSlash(directoryPathRelativeToDirectoryObject);
		return createInstance(relativeFileName);
	}

	// Setup ==================================================================

	private DirectoryObject(String relativeFileName) {
		super(relativeFileName);
	}

	// Implemented ============================================================

	@Override
        public boolean isDirectory() {
		return true;
	}

	// Accessing ==============================================================

	public final boolean isParentOf(AbstractFileObject abstractFileObject) {
		final String directoryPath = getPath();
		final String path = abstractFileObject.getPath();
		if (path.length() <= directoryPath.length()) {
			return false;
		}
		if (!path.startsWith(directoryPath)) {
			return false;
		}
		return true;
	}
}
