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
package org.netbeans.lib.cvsclient.admin;

import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileUtils;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.File;
import java.io.IOException;

/**
 * @author  Thomas Singer
 */
public final class AdminUtils {

	public static String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
		final File directory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
		return getStickyTagForDirectory(directory);
	}

	public static String getStickyTagForDirectory(File directory) {
		final File tagFile = new File(directory, "CVS/Tag");

		if (!tagFile.isFile()) {
			return null;
		}

		try {
			return FileUtils.readLineFromFile(tagFile);
		}
		catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static File createEntriesFile(File directory) {
		return new File(directory, "CVS/Entries");
	}

	public static File createEntriesDotLogFile(File directory) {
		return new File(directory, "CVS/Entries.Log");
	}
}
