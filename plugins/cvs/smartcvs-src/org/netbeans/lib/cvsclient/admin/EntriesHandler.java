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

import java.io.File;
import java.io.IOException;

/**
 * @author  Thomas Singer
 */
public final class EntriesHandler {

	// Fields =================================================================

	private final Entries entries = new Entries();
	private final File entriesFile;
	private final File entriesDotLogFile;

	// Setup ==================================================================

	public EntriesHandler(File directory) {
		BugLog.getInstance().assertNotNull(directory);

		entriesFile = AdminUtils.createEntriesFile(directory);
		entriesDotLogFile = AdminUtils.createEntriesDotLogFile(directory);
	}

	// Accessing ==============================================================

	public Entries getEntries() {
		return entries;
	}

	// Actions ================================================================

	public boolean read(String charsetName) throws IOException {
    synchronized(Entries.class){
		  entries.read(entriesFile, charsetName);
      return new EntriesDotLog().readAndApply(entriesDotLogFile, entries);
    }

	}

	public void write(String lineSeparator, final String charsetName) throws IOException {
    synchronized(Entries.class){
		  entries.write(entriesFile, lineSeparator, charsetName);
      entriesDotLogFile.delete();
    }
	}

	public void readAndWrite(final String lineSeparator, final String charsetName) throws IOException {
		if (read(charsetName)) {
			write(lineSeparator,charsetName);
		}
	}
}
