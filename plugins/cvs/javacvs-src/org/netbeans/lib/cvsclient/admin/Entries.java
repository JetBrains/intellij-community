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

import com.intellij.openapi.util.io.FileUtil;
import org.netbeans.lib.cvsclient.util.BugLog;
import org.netbeans.lib.cvsclient.JavaCvsSrcBundle;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.*;

/**
 * @author Thomas Singer
 */
public final class Entries {

  // Fields =================================================================

  private final Map<String, Entry> fileNameToEntryMap = new HashMap<String, Entry>();
  @NonNls private static final String DIRECTORY_PREFIX = "D";

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
    return fileNameToEntryMap.get(fileName);
  }

  public Collection<Entry> getEntries() {
    return Collections.unmodifiableCollection(fileNameToEntryMap.values());
  }

  public void getEntries(Collection<Entry> collection) {
    collection.addAll(fileNameToEntryMap.values());
  }

  public int size() {
    return fileNameToEntryMap.size();
  }

  // Actions ================================================================

  public void read(File entriesFile, final String charsetName) throws IOException {
    try {
      read(new InputStreamReader(new FileInputStream(entriesFile), charsetName));
    }
    catch (InvalidEntryFormatException ex) {
      ex.setEntriesFile(entriesFile);
      throw ex;
    }
  }

  public void read(Reader reader) throws IOException {
    final BufferedReader lineReader = new BufferedReader(reader);
    try {
      for (String line = lineReader.readLine(); line != null; line = lineReader.readLine()) {
        if (line.trim().length() == 0) {
          continue;
        }

        if (line.startsWith(DIRECTORY_PREFIX) && line.trim().length() == 1) {
          continue;
        }

        addEntry(Entry.createEntryForLine(line));
      }
    }
    finally {
      lineReader.close();
    }
  }

  public void write(File entriesFile, String lineSeparator, final String charsetName) throws IOException {
    final File tempFile = new File(entriesFile.getAbsolutePath() + "~");
    FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
    final OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, charsetName);
    write(writer, lineSeparator);
    if (entriesFile.exists()) {
      if (!entriesFile.delete()) {
        throw new IOException(JavaCvsSrcBundle.message("could.not.delete.file.error.message", entriesFile.getAbsolutePath()));
      }
    }

    FileUtil.rename(tempFile, entriesFile);
  }

  public void write(Writer writer, String lineSeparator) throws IOException {
    writer = new BufferedWriter(writer);
    try {
      if (fileNameToEntryMap.size() == 0) {
        writer.write(DIRECTORY_PREFIX);
        writer.write(lineSeparator);
      }
      else {
        final Entry[] entryArray = new Entry[fileNameToEntryMap.size()];
        fileNameToEntryMap.values().toArray(entryArray);
        Arrays.sort(entryArray, new EntriesComparator());
        for (final Entry entry : entryArray) {
          writer.write(entry.toString());
          writer.write(lineSeparator);
        }
      }
    }
    finally {
      writer.close();
    }
  }

  // Inner classes ==========================================================

  private static final class EntriesComparator implements Comparator<Entry> {

    public int compare(Entry entry1, Entry entry2) {
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
