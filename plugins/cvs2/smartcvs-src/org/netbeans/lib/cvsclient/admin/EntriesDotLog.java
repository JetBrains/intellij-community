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

import org.netbeans.lib.cvsclient.util.BugLog;
import org.jetbrains.annotations.NonNls;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author  Thomas Singer
 */
final class EntriesDotLog {

	// Constants ==============================================================

	@NonNls private static final String ENTRY_ADDED = "A ";
	@NonNls private static final String ENTRY_REMOVED = "R ";

	// Actions ================================================================

	public boolean readAndApply(File entriesDotLogFile, Entries entries) throws IOException {
		BugLog.getInstance().assertNotNull(entriesDotLogFile);
		BugLog.getInstance().assertNotNull(entries);

		if (!entriesDotLogFile.exists()) {
			return false;
		}

		final BufferedReader reader = new BufferedReader(new FileReader(entriesDotLogFile));
		try {
			boolean entriesUpdated = false;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.startsWith(ENTRY_ADDED)) {
					final String entryLine = line.substring(ENTRY_ADDED.length());
					final Entry entry = Entry.createEntryForLine(entryLine);
					entries.addEntry(entry);
					entriesUpdated = true;
				}
				else if (line.startsWith(ENTRY_REMOVED)) {
					final String entryLine = line.substring(ENTRY_REMOVED.length());
					final Entry entry = Entry.createEntryForLine(entryLine);
					if (entry != null) {
						entries.removeEntry(entry.getFileName());
						entriesUpdated = true;
					}
				}
			}
			return entriesUpdated;
		}
		finally {
			try {
				reader.close();
			}
			catch (IOException ex) {
        ex.printStackTrace();
				// ignore
			}
		}
	}
}
