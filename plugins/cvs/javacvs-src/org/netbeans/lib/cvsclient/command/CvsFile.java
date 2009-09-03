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
package org.netbeans.lib.cvsclient.command;

import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * @author  Thomas Singer
 */
final class CvsFile {

	// Static =================================================================

	public static CvsFile createCvsFileForEntry(FileObject fileObject, Entry entry, boolean exists) {
		BugLog.getInstance().assertNotNull(fileObject);
		BugLog.getInstance().assertNotNull(entry);

		return new CvsFile(fileObject, exists, entry, false);
	}

	public static CvsFile createCvsFileForExistingFile(FileObject fileObject) {
		BugLog.getInstance().assertNotNull(fileObject);

		return new CvsFile(fileObject, true, null, false);
	}

	public static CvsFile createCvsDirectory(DirectoryObject directoryObject) {
		BugLog.getInstance().assertNotNull(directoryObject);

		return new CvsFile(directoryObject, true, null, true);
	}

	// Fields =================================================================

	private final AbstractFileObject fileObject;
	private final Entry entry;
	private final boolean directory;
	private final boolean exists;

	// Setup ==================================================================

	private CvsFile(AbstractFileObject fileObject, boolean exists, Entry entry, boolean directory) {
		BugLog.getInstance().assertNotNull(fileObject);

		this.fileObject = fileObject;
		this.exists = exists;
		this.entry = entry;
		this.directory = directory;
	}

	// Accessing ==============================================================

	public boolean isDirectory() {
		return directory;
	}

	public Entry getEntry() {
		return entry;
	}

	public AbstractFileObject getFileObject() {
		return fileObject;
	}

	public boolean exists() {
		return exists;
	}

	// Implemented ============================================================

	@SuppressWarnings({"HardCodedStringLiteral"})
        public String toString() {
		return "fileObject='" + fileObject + "', entry=" + (entry != null ? "'" + entry + "'" : null);
	}
}
