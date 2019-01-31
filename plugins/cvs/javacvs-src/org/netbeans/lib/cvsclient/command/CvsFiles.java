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

import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.util.ArrayList;
import java.util.List;

/**
 * @author  Thomas Singer
 */
final class CvsFiles
        implements ICvsFiles {

	// Fields =================================================================

	private final List<CvsFile> cvsFiles = new ArrayList<>();
	private CvsFile currentCvsDirectory;

	// Setup ==================================================================

	CvsFiles() {
	}

	// Implemented ============================================================

	@Override
        public void visit(ICvsFilesVisitor visitor) {
		BugLog.getInstance().assertNotNull(visitor);

		for (CvsFile cvsFile : cvsFiles) {
			if (cvsFile.isDirectory()) {
				visitor.handleDirectory((DirectoryObject)cvsFile.getFileObject());
			}
			else {
				visitor.handleFile((FileObject)cvsFile.getFileObject(), cvsFile.getEntry(), cvsFile.exists());
			}
		}
	}

	// Actions ================================================================

	public void clear() {
		cvsFiles.clear();
		currentCvsDirectory = null;
	}

	public void add(CvsFile cvsFile) {
		if (currentCvsDirectory == null) {
			BugLog.getInstance().assertTrue(cvsFile.isDirectory(), "The first cvsFile must be a directory.");
			currentCvsDirectory = cvsFile;
		}
		else {
			if (cvsFile.isDirectory()) {
				if (currentCvsDirectory.getFileObject().getPath().equals(cvsFile.getFileObject().getPath())) {
					// do not add the same directory twice
					return;
				}

				currentCvsDirectory = cvsFile;
			}
		}
		cvsFiles.add(cvsFile);
	}
}
