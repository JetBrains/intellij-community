/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.admin;

import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The class abstracts the CVS concept of an <i>entry line</i>. The entry
 * line is textually of the form:<p>
 * / name / version / conflict / options / tag_or_date
 * <p>These are explained in section 5.1 of the CVS protocol 1.10 document.
 *
 * @author Robert Greig
 */
public final class Entry implements Cloneable {

  // Constants ==============================================================

  @NonNls public static final String DUMMY_TIMESTAMP = "dummy timestamp";
  @NonNls private static final String DUMMY_TIMESTAMP_NEW_ENTRY = "dummy timestamp from new-entry";
  @NonNls private static final String MERGE_TIMESTAMP = "Result of merge";

  @NonNls private static final String STICKY_TAG_REVISION_PREFIX = "T";
  @NonNls private static final String STICKY_DATE_PREFIX = "D";

  @NonNls private static final String BINARY_FILE = "-kb";

  @NonNls private static final String HAD_CONFLICTS = "+";
  @NonNls private static final char TIMESTAMP_MATCHES_FILE = '=';

  @NonNls private static final String DIRECTORY_PREFIX = "D/";

  // Static =================================================================

  @NonNls private static final String DATE_FORMAT_STR = "yyyy.MM.dd.hh.mm.ss";
  private static final SyncDateFormat STICKY_DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat(DATE_FORMAT_STR));

  private static SyncDateFormat lastModifiedDateFormatter;
  private boolean isAddedFile = false;
  private boolean isRemoved = false;
  private boolean isResultOfMerge = false;
  @NonNls private static final String LAST_MODIFIED_DATE_FORMAT_ATR = "EEE MMM dd HH:mm:ss yyyy";
  @NonNls private static final String TIME_ZONE_FORMAT_STR = "GMT+0000";
  @NonNls private static final String INITIAL_PREFIX = "Initial ";

  public static SyncDateFormat getLastModifiedDateFormatter() {
    if (lastModifiedDateFormatter == null) {
      lastModifiedDateFormatter = new SyncDateFormat(new SimpleDateFormat(LAST_MODIFIED_DATE_FORMAT_ATR, Locale.US));
      lastModifiedDateFormatter.setTimeZone(TimeZone.getTimeZone(TIME_ZONE_FORMAT_STR));
    }
    return lastModifiedDateFormatter;
  }

  public static String formatLastModifiedDate(Date date) {
    return getLastModifiedDateFormatter().format(date);
  }

  public static Entry createDirectoryEntry(String directoryName) {
    final Entry entry = new Entry();
    entry.setFileName(directoryName);
    entry.setDirectory(true);
    return entry;
  }

  public static Entry createEntryForLine(String entryLine) {
    final Entry entry = new Entry();
    entry.parseLine(entryLine);
    return entry;
  }

  // Fields =================================================================

  private boolean directory;
  private String fileName;
  private Date lastModified;
  private String revision;
  private boolean conflict;
  private boolean timeStampMatchesFile;
  private String conflictString;
  private String conflictStringWithoutConflictMarker;
  private String options;

  private String stickyRevision;
  private String stickyTag;
  private String stickyDateString;
  private Date stickyDate;

  // Setup ==================================================================

  private Entry() {
  }

  // Implemented ============================================================

  /**
   * Create a string representation of the entry line.
   * Create the standard CVS 1.10 entry line format.
   */
  public String toString() {
    final StringBuffer buf = new StringBuffer();
    if (directory) {
      buf.append(DIRECTORY_PREFIX);
    }
    else {
      buf.append('/');
    }
    // if name is null, then this is a totally empty entry, so append
    // nothing further
    if (fileName != null) {
      buf.append(fileName);
      buf.append('/');
      if (revision != null) {
        buf.append(revision);
      }
      buf.append('/');
      if (conflictString != null) {
        buf.append(conflictString);
      }
      buf.append('/');
      if (options != null) {
        buf.append(options);
      }
      buf.append('/');
      buf.append(getStickyData());
    }
    return buf.toString();
  }

  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }

    final String entryFileName = ((Entry)obj).fileName;
    return (fileName == entryFileName) || (fileName != null && fileName.equals(entryFileName));
  }

  public int hashCode() {
    return (fileName != null) ? fileName.hashCode() : 0;
  }

  // Accessing ==============================================================

  public String getFileName() {
    return fileName;
  }

  private void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getRevision() {
    return revision;
  }

  public void setRevision(String revision) {
    this.revision = revision;
    isAddedFile = revision != null && revision.startsWith("0");
    isRemoved = revision != null && revision.startsWith("-");

  }

  public Date getLastModified() {
    return lastModified;
  }

  public void setDummyTimestamp() {
    parseConflictString(DUMMY_TIMESTAMP);
  }

  public boolean isResultOfMerge() {
    return isResultOfMerge;
  }

  public void setConflict(String conflictString) {
    this.conflictString = conflictString;
    isResultOfMerge = conflictString != null && conflictString.startsWith(MERGE_TIMESTAMP);
  }

  /**
   * A typical conflict string looks like "+=".
   */
  public void parseConflictString(String conflictString) {
    setConflict(conflictString);
    this.conflictStringWithoutConflictMarker = conflictString;
    this.lastModified = null;
    this.conflict = false;
    this.timeStampMatchesFile = false;

    if (conflictString == null || conflictString.equals(DUMMY_TIMESTAMP) || conflictString.equals(MERGE_TIMESTAMP) ||
        conflictString.equals(DUMMY_TIMESTAMP_NEW_ENTRY)) {
      return;
    }

    int parseStartIndex = 0;
    // Look for the position of + which indicates a conflict
    final int conflictIndex = conflictStringWithoutConflictMarker.indexOf(HAD_CONFLICTS);
    if (conflictIndex >= 0) {
      conflict = true;
      parseStartIndex = conflictIndex + 1;
    }
    // if the timestamp matches the file, there will be an = following
    // the +
    final int timeMatchIndex = conflictStringWithoutConflictMarker.indexOf(TIMESTAMP_MATCHES_FILE);
    if (timeMatchIndex >= 0) {
      timeStampMatchesFile = true;
      parseStartIndex = Math.max(parseStartIndex, timeMatchIndex + 1);
    }

    // At this point the conflict index tells us where the real conflict
    // string starts
    if (parseStartIndex > 0) {
      conflictStringWithoutConflictMarker = conflictStringWithoutConflictMarker.substring(parseStartIndex);
    }

    // if we have nothing after the = then don't try to parse it
    if (conflictStringWithoutConflictMarker.length() == 0) {
      conflictStringWithoutConflictMarker = null;
      return;
    }

    if (conflictStringWithoutConflictMarker.startsWith(INITIAL_PREFIX)) {
      return;
    }

    try {
      this.lastModified = getLastModifiedDateFormatter().parse(conflictStringWithoutConflictMarker);
    }
    catch (Exception ex) {
      lastModified = null;
      //noinspection HardCodedStringLiteral
      System.err.println("[Entry] can't parse conflict '" + conflictStringWithoutConflictMarker + "'");
    }
  }

  public String getOptions() {
    return options;
  }

  public String getStickyTag() {
    return stickyTag;
  }

  public void setStickyTag(String stickyTag) {
    this.stickyTag = stickyTag;
    this.stickyRevision = null;
    this.stickyDateString = null;
    this.stickyDate = null;
  }

  public String getStickyRevision() {
    return stickyRevision;
  }

  public void setStickyRevision(String stickyRevision) {
    this.stickyTag = null;
    this.stickyRevision = stickyRevision;
    this.stickyDateString = null;
    this.stickyDate = null;
  }

  public String getStickyDateString() {
    return stickyDateString;
  }

  public void setStickyDateString(String stickyDateString) {
    this.stickyTag = null;
    this.stickyRevision = null;
    this.stickyDateString = stickyDateString;
    this.stickyDate = null;
  }

  public Date getStickyDate() {
    // lazy generation
    if (stickyDate != null) {
      return stickyDate;
    }
    if (stickyDateString == null) {
      return null;
    }

    try {
      return STICKY_DATE_FORMAT.parse(stickyDateString);
    }
    catch (ParseException ex) {
      // ignore silently
      return null;
    }
  }

  public void setStickyDate(Date stickyDate) {
    if (stickyDate == null) {
      this.stickyTag = null;
      this.stickyRevision = null;
      this.stickyDateString = null;
      this.stickyDate = null;
      return;
    }

    this.stickyTag = null;
    this.stickyRevision = null;
    this.stickyDateString = STICKY_DATE_FORMAT.format(stickyDate);
    this.stickyDate = stickyDate;
  }

  public String getStickyInformation() {
    if (stickyTag != null) {
      return stickyTag;
    }
    if (stickyRevision != null) {
      return stickyRevision;
    }
    return stickyDateString;
  }

  public void setStickyInformation(String stickyInformation) {
    if (stickyInformation == null) {
      resetStickyInformation();
      return;
    }

    if (stickyInformation.startsWith(STICKY_TAG_REVISION_PREFIX)) {
      final String tagOrRevision = stickyInformation.substring(STICKY_TAG_REVISION_PREFIX.length());
      if (tagOrRevision.length() == 0) {
        resetStickyInformation();
        return;
      }

      final char firstChar = tagOrRevision.charAt(0);
      if (firstChar >= '0' && firstChar <= '9') {
        setStickyRevision(tagOrRevision);
      }
      else {
        setStickyTag(tagOrRevision);
      }
      return;
    }

    if (stickyInformation.startsWith(STICKY_DATE_PREFIX)) {
      setStickyDateString(stickyInformation.substring(STICKY_DATE_PREFIX.length()));
    }

    // Ignore other cases silently
  }

  public boolean isBinary() {
    return options != null && options.equals(BINARY_FILE);
  }

  public boolean isUnicode() {
    if (options == null || !options.startsWith("-k")) return false;
    return options.indexOf('u', 2) >= 0;
  }

  public boolean isAddedFile() {
    return isAddedFile;
  }

  public boolean isRemoved() {
    return isRemoved;
  }

  public boolean isValid() {
    return getFileName() != null && getFileName().length() > 0;
  }

  public boolean isDirectory() {
    return directory;
  }

  private void setDirectory(boolean directory) {
    this.directory = directory;
  }

  public boolean isConflict() {
    return conflict;
  }

  public String getConflictStringWithoutConflict() {
    return conflictStringWithoutConflictMarker;
  }

  public boolean isTimeStampMatchesFile() {
    return timeStampMatchesFile;
  }

  // Utils ==================================================================

  private void parseLine(String entryLine) {
    // try to parse the entry line, if we get stuck just
    // throw an illegal argument exception

    if (entryLine.startsWith(DIRECTORY_PREFIX)) {
      directory = true;
      entryLine = entryLine.substring(1);
    }

    // first character is a slash, so name is read from position 1
    // up to the next slash
    final int[] slashPositions = new int[5];

    slashPositions[0] = 0;
    for (int i = 1; i < 5; i++) {
      slashPositions[i] = entryLine.indexOf('/', slashPositions[i - 1] + 1);
    }

    // Test if this is a D on its own, a special case indicating that
    // directories are understood and there are no subdirectories
    // in the current folder
    if (slashPositions[1] < 1) {
      throw new InvalidEntryFormatException();
    }

    // note that the parameters to substring are treated as follows:
    // (inclusive, exclusive)
    fileName = entryLine.substring(slashPositions[0] + 1, slashPositions[1]);
    setRevision(entryLine.substring(slashPositions[1] + 1, slashPositions[2]));
    if ((slashPositions[3] - slashPositions[2]) > 1) {
      final String conflict = entryLine.substring(slashPositions[2] + 1, slashPositions[3]);
      parseConflictString(conflict);
    }
    if ((slashPositions[4] - slashPositions[3]) > 1) {
      options = entryLine.substring(slashPositions[3] + 1, slashPositions[4]);
    }
    if (slashPositions[4] != (entryLine.length() - 1)) {
      final String tagOrDate = entryLine.substring(slashPositions[4] + 1);
      setStickyInformation(tagOrDate);
    }
  }

  private void resetStickyInformation() {
    stickyTag = null;
    stickyRevision = null;
    stickyDateString = null;
    stickyDate = null;
  }

  public String getStickyData() {
    StringBuffer buf = new StringBuffer();
    if (stickyTag != null) {
      buf.append(STICKY_TAG_REVISION_PREFIX);
      buf.append(stickyTag);
    }
    else if (stickyRevision != null) {
      buf.append(STICKY_TAG_REVISION_PREFIX);
      buf.append(stickyRevision);
    }
    else if (stickyDateString != null) {
      buf.append(STICKY_DATE_PREFIX);
      buf.append(getStickyDateString());
    }

    return buf.toString();
  }

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
