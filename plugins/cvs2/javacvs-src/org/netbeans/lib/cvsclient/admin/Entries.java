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

import java.io.*;
import java.util.*;

/**
 * @author Thomas Singer
 */
public final class Entries {

	// Fields =================================================================

	private final Map fileNameToEntryMap = new HashMap();

	// Setup ==================================================================

	public Entries() {
	}

	// Accessing ==============================================================

	public void addEntry(Entry entry) {
		BugLog.getInstance().assertNotNull(entry);
		BugLog.getInstance().assertNotNull(entry.getFileName());

		fileNameToEntryMap.put(entry.getFileName(), entry);
	}

	public boolean removeEntry(String fileName) {
		BugLog.getInstance().assertNotNull(fileName);

		return fileNameToEntryMap.remove(fileName) != null;
	}

	public Entry getEntry(String fileName) {
		return (Entry)fileNameToEntryMap.get(fileName);
	}

	public Collection getEntries() {
		return Collections.unmodifiableCollection(fileNameToEntryMap.values());
	}

	public void getEntries(Collection collection) {
		collection.addAll(fileNameToEntryMap.values());
	}

	public int size() {
		return fileNameToEntryMap.size();
	}

	// Actions ================================================================

	public void read(File entriesFile) throws IOException {
		try {
			read(new FileReader(entriesFile));
		}
		catch (InvalidEntryFormatException ex) {
			ex.setEntriesFile(entriesFile);
			throw ex;
		}
	}

	public void read(Reader reader) throws IOException {
		final BufferedReader lineReader = new BufferedReader(reader);
		try {
			for (String line = lineReader.readLine();
			     line != null;
			     line = lineReader.readLine()) {
				if (line.trim().length() == 0) {
					continue;
				}

				if (line.startsWith("D") && line.trim().length() == 1) {
					continue;
				}

				addEntry(Entry.createEntryForLine(line));
			}
		}
		finally {
			lineReader.close();
		}
	}

	public void write(File entriesFile) throws IOException {
		final File tempFile = new File(entriesFile.getAbsolutePath() + "~");
		write(new FileWriter(tempFile));
		if (entriesFile.exists()) {
			if (!entriesFile.delete()) {
				throw new IOException("Could not delete file " + entriesFile.getAbsolutePath());
			}
		}

		if (!tempFile.renameTo(entriesFile)) {
			throw new IOException("Could not rename file " + tempFile.getAbsolutePath() + " to " + entriesFile.getAbsolutePath());
		}
	}

	public void write(Writer writer) throws IOException {
		final BufferedWriter lineWriter = new BufferedWriter(writer);
		try {
			if (fileNameToEntryMap.size() == 0) {
				lineWriter.write("D");
				lineWriter.newLine();
			}
			else {
				final Entry[] entryArray = new Entry[fileNameToEntryMap.size()];
				fileNameToEntryMap.values().toArray(entryArray);
				Arrays.sort(entryArray, new EntriesComparator());
				for (int i = 0; i < entryArray.length; i++) {
					final Entry entry = entryArray[i];
					lineWriter.write(entry.toString());
					lineWriter.newLine();
				}
			}
		}
		finally {
			lineWriter.close();
		}
	}

	// Inner classes ==========================================================

	private static final class EntriesComparator implements Comparator {

		public int compare(Object obj1, Object obj2) {
			final Entry entry1 = (Entry)obj1;
			final Entry entry2 = (Entry)obj2;
			if (entry1.isDirectory() != entry2.isDirectory()) {
				if (entry1.isDirectory()) {
					return -1;
				}
				return +1;
			}
			return entry1.getFileName().compareTo(entry2.getFileName());
		}
	}
}
