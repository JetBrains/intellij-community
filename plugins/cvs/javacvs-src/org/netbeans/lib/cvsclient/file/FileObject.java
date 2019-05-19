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

import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * Thiz represents a relative file name relative to an root entry (normally directory).
 *
 * @author  Thomas Singer
 */
public final class FileObject extends AbstractFileObject {

	// Static =================================================================

	public static FileObject createInstance(String relativeFileName) {
		return new FileObject(relativeFileName);
	}

	public static FileObject createInstance(DirectoryObject directoryObject, String filePathRelativeToDirectoryObject) {
		return createInstance(FileUtils.ensureTrailingSlash(directoryObject.getPath())
		                      + FileUtils.removeLeadingSlash(filePathRelativeToDirectoryObject));
	}

	// Setup ==================================================================

	private FileObject(String path) {
		super(path);
		BugLog.getInstance().assertTrue(!path.endsWith("/"), "'" + path + "' must end with a /");
	}

	// Implemented ============================================================

	@Override
        public boolean isDirectory() {
		return false;
	}
}
