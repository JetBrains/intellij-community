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
package org.netbeans.lib.cvsclient.command.update;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.IFileInfo;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.File;

/**
 * @author  Thomas Singer
 */
public final class UpdateFileInfo
        implements IFileInfo {

	// Fields =================================================================

	private final FileObject fileObject;
	private final File file;
	private String type;
	private Entry entry;

	// Setup ==================================================================

	public UpdateFileInfo(FileObject fileObject, File file) {
		BugLog.getInstance().assertNotNull(fileObject);
		BugLog.getInstance().assertNotNull(file);

		this.fileObject = fileObject;
		this.file = file;
	}

	// Implemented ============================================================

	@Override
        public FileObject getFileObject() {
		return fileObject;
	}

	public String toString() {
		@NonNls final StringBuilder buffer = new StringBuilder();
		buffer.append(type);
		buffer.append("  ");
		if (isDirectory()) {
			buffer.append("Directory ");
		}
		buffer.append(file != null ? file.getAbsolutePath() : "null");
		return buffer.toString();
	}

	// Accessing ==============================================================

	public File getFile() {
		return file;
	}

	public Entry getEntry() {
		return entry;
	}

	public String getType() {
		return type;
	}

	void setEntry(Entry entry) {
		this.entry = entry;
	}

	void setType(@NonNls String type) {
		this.type = type;
	}

	// Utils ==================================================================

	private boolean isDirectory() {
		final File file = getFile();
		if (file == null) {
			return false;
		}
		return file.isDirectory();
	}
}
