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
package org.netbeans.lib.cvsclient.command.log;

import org.netbeans.lib.cvsclient.command.AbstractMessageParser;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Date;

/**
 * @author Thomas Singer
 */
final class LogMessageParser extends AbstractMessageParser {

  // Constants ==============================================================


  private static final String RCS_FILE = "RCS file: ";
  private static final String WORKING_FILE = "Working file: ";
  private static final String HEAD = "head: ";
  private static final String BRANCH = "branch:";
  private static final String LOCKS = "locks: ";
  private static final String ACCESS_LIST = "access list:";
  private static final String SYMBOLIC_NAMES = "symbolic names:";
  private static final String KEYWORD_SUBST = "keyword substitution: ";
  private static final String TOTAL_REVISIONS = "total revisions: ";
  private static final String SELECTED_REVISIONS = ";\tselected revisions: ";
  private static final String DESCRIPTION = "description:";
  private static final String REVISION = "revision ";
  private static final String DATE = "date: ";
  private static final String BRANCHES = "branches: ";
  private static final String AUTHOR = "  author: ";
  private static final String STATE = "  state: ";
  private static final String LINES = "  lines: ";
  private static final String SPLITTER = "----------------------------";
  private static final String FINAL_SPLIT = "=============";
  private static final String FINAL_SPLIT_WITH_TAB = "\t=============";

  private static final SimpleDateFormat[] EXPECTED_DATE_FORMATS = new SimpleDateFormat[3];

  static {
    EXPECTED_DATE_FORMATS[0] = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    EXPECTED_DATE_FORMATS[0].setTimeZone(TimeZone.getTimeZone("GMT"));

    EXPECTED_DATE_FORMATS[1] = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    EXPECTED_DATE_FORMATS[1].setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  // Fields =================================================================

  private final IEventSender eventSender;
  private final ICvsFileSystem cvsFileSystem;
  private LogInformation logInfo;
  private Revision revision;
  private boolean addingSymNames;
  private boolean addingDescription;
  private boolean processingRevision;
  private StringBuffer tempBuffer;
  private String lastLogMessageString = null;

  // Setup ==================================================================

  public LogMessageParser(IEventSender eventSender,
                          ICvsFileSystem cvsFileSystem) {
    BugLog.getInstance().assertNotNull(eventSender);
    BugLog.getInstance().assertNotNull(cvsFileSystem);

    this.cvsFileSystem = cvsFileSystem;
    this.eventSender = eventSender;
  }

  // Implemented ============================================================

  protected void outputDone() {

    if (addingDescription) {
      addingDescription = false;
      logInfo.setDescription(tempBuffer.toString());
    }
    if (processingRevision) {
      logInfo.addRevision(revision);
      if ((lastLogMessageString != null) && !lastLogMessageIsFinalSeparator()) {
        appendLastLogMessageStringToTempBuffer();
      }
      revision.setMessage(tempBuffer.toString());

      revision = null;
      processingRevision = false;

    }
    if (logInfo != null) {
      eventSender.notifyFileInfoListeners(logInfo);
      logInfo = null;
    }

    tempBuffer = null;
    lastLogMessageString = null;

  }

  private boolean lastLogMessageIsFinalSeparator() {
    return lastLogMessageString.startsWith(FINAL_SPLIT) || lastLogMessageString.startsWith(FINAL_SPLIT_WITH_TAB);
  }

  public void parseLine(String line,
                        boolean isErrorMessage) {
    if (isErrorMessage) return;
    if (processingRevision) {
      if (line.startsWith(RCS_FILE)) {
        processRcsFile(line.substring(RCS_FILE.length()));
        return;
      }

      if (line.startsWith(REVISION)) {
        processRevisionStart(line);
        return;
      }

      if (line.startsWith(DATE)) {
        processRevisionDate(line);
        return;
      }


      // first check for the branches tag
      if (line.startsWith(BRANCHES)) {
        processBranches(line.substring(BRANCHES.length()));
      }
      else {
        if (lastLogMessageString != null) {
          appendLastLogMessageStringToTempBuffer();
        }
        lastLogMessageString = line;
      }
      return;
    }
    if (addingSymNames) {
      if (line.startsWith("\t")) {
        processSymbolicNames(line.substring(1));
        return;
      }
    }
    // revision stuff first -> will be  the most common to parse
    if (line.startsWith(REVISION)) {
      processRevisionStart(line);
      return;
    }
    if (line.startsWith(KEYWORD_SUBST)) {
      final String keywordSubstitution = line.substring(KEYWORD_SUBST.length()).trim();
      logInfo.setKeywordSubstitution(KeywordSubstitution.getValue(keywordSubstitution));
      addingSymNames = false;
      return;
    }

    if (line.startsWith(RCS_FILE)) {
      processRcsFile(line.substring(RCS_FILE.length()));
      return;
    }
    if (line.startsWith(WORKING_FILE)) {
      processWorkingFile(line.substring(WORKING_FILE.length()));
      return;
    }
    if (line.startsWith(HEAD)) {
      logInfo.setHeadRevision(line.substring(HEAD.length()).trim());
      return;
    }
    if (line.startsWith(BRANCH)) {
      logInfo.setBranch(line.substring(BRANCH.length()).trim());
      return;
    }
    if (line.startsWith(LOCKS)) {
      logInfo.setLocks(line.substring(LOCKS.length()).trim());
      return;
    }
    if (line.startsWith(ACCESS_LIST)) {
      logInfo.setAccessList(line.substring(ACCESS_LIST.length()).trim());
      return;
    }
    if (line.startsWith(SYMBOLIC_NAMES)) {
      addingSymNames = true;
      return;
    }
    if (line.startsWith(TOTAL_REVISIONS)) {
      final String separator = SELECTED_REVISIONS;
      final int semicolonIndex = line.indexOf(separator);
      if (semicolonIndex < 0) {
        // no selected revisions here..
        logInfo.setTotalRevisions(line.substring(TOTAL_REVISIONS.length()).trim());
        logInfo.setSelectedRevisions("0");
      }
      else {
        final String totalRevisions = line.substring(0, semicolonIndex);
        final String selectedRevisions = line.substring(semicolonIndex);
        logInfo.setTotalRevisions(totalRevisions.substring(TOTAL_REVISIONS.length()).trim());
        logInfo.setSelectedRevisions(selectedRevisions.substring(SELECTED_REVISIONS.length()).trim());
      }
      return;
    }

    if (addingDescription) {
      if (!processingRevision && line.startsWith(SPLITTER)) {
        return;
      }
      if (lastLogMessageString != null) {
        appendLastLogMessageStringToTempBuffer();
      }
      lastLogMessageString = line;
      return;
    }

    if (line.startsWith(DESCRIPTION)) {
      tempBuffer = new StringBuffer(line.substring(DESCRIPTION.length()));
      addingDescription = true;
      return;
    }
  }

  private void appendLastLogMessageStringToTempBuffer() {
    tempBuffer.append(lastLogMessageString);
    tempBuffer.append('\n');
  }

  // Utils ==================================================================

  private void processRcsFile(String line) {
    if (logInfo != null) {
      outputDone();
    }
    logInfo = new LogInformation();
    logInfo.setRcsFileName(line.trim());
  }

  private void processWorkingFile(String line) {
    String fileName = line.trim();
    if (fileName.startsWith("no file")) {
      fileName = fileName.substring(8);
    }

    logInfo.setFile(createFile(fileName));
  }

  private void processBranches(String line) {
    final int ind = line.lastIndexOf(';');
    if (ind > 0) {
      line = line.substring(0, ind);
    }
    revision.setBranches(line.trim());
  }

  private void processSymbolicNames(String line) {
    final int index = line.lastIndexOf(':');
    if (index < 0) {
      return;
    }

    final String symName = line.substring(0, index).trim();
    final String revName = line.substring(index + 1, line.length()).trim();
    logInfo.addSymbolicName(symName, revName);
  }

  private void processRevisionStart(String line) {
    revisionProcessingFinished();

    int tabIndex = line.indexOf('\t', REVISION.length());
    if (tabIndex < 0) {
      tabIndex = line.length();
    }

    final String revisionNumber = line.substring(REVISION.length(), tabIndex);
    revision = new Revision(revisionNumber);
    processingRevision = true;
  }

  private void revisionProcessingFinished() {
    if (revision != null) {
      if ((lastLogMessageString != null) && !lastLogMessageString.startsWith(SPLITTER)) {
        appendLastLogMessageStringToTempBuffer();
      }
      lastLogMessageString = null;
      processingRevision = false;
      revision.setMessage(tempBuffer.toString());

      logInfo.addRevision(revision);
    }
  }

  private void processRevisionDate(String line) {
    // a line may looks like:
    // date: 2003/02/20 14:52:06;  author: tom;  state: Exp;  lines: +1 -1; kopt: o; commitid: 3803e54eb96167d;
    // or:
    // date: 2003/01/11 17:56:27;  author: tom;  state: Exp;
    final StringTokenizer token = new StringTokenizer(line, ";", false);
    if (token.hasMoreTokens()) {
      final String date = token.nextToken();
      final String dateString = date.substring(DATE.length());
      Date parsedDate = null;
      for (int i = 0; i < EXPECTED_DATE_FORMATS.length; i++) {
        SimpleDateFormat expectedDateFormat = EXPECTED_DATE_FORMATS[i];
        try {
          parsedDate = expectedDateFormat.parse(dateString);
        }
        catch (ParseException e) {

        }
        if (parsedDate != null) break;
      }
      if (parsedDate != null) {
        revision.setDate(parsedDate);
      }
      else {
        BugLog.getInstance().showException(new Exception("Line " + line + " could be be parsed."));
      }
    }
    if (token.hasMoreTokens()) {
      final String author = token.nextToken();
      if (author.startsWith(AUTHOR)) {
        revision.setAuthor(author.substring(AUTHOR.length()));
      }
    }
    if (token.hasMoreTokens()) {
      final String state = token.nextToken();
      if (state.startsWith(STATE)) {
        revision.setState(state.substring(STATE.length()));
      }
    }
    if (token.hasMoreTokens()) {
      final String linesModified = token.nextToken();
      if (linesModified.startsWith(LINES)) {
        revision.setLines(linesModified.substring(LINES.length()));
      }
    }

    processingRevision = true;
    tempBuffer = new StringBuffer();
  }

  private File createFile(String fileName) {
    return cvsFileSystem.getLocalFileSystem().getFile(fileName);
  }
}
