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
import org.netbeans.lib.cvsclient.command.AbstractMessageParser;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEntryListener;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Thomas Singer
 */
public final class UpdateMessageParser extends AbstractMessageParser implements IEntryListener {

  // Constants ==============================================================

  @NonNls private static final String EXAM_DIR = "server: Updating ";
  @NonNls private static final String UNKNOWN = "server: nothing known about";
  @NonNls private static final String TO_ADD = "server: use `cvs add' to create an entry for";
  @NonNls private static final String STATES = "UPARMC?";
  @NonNls private static final String WARNING = "server: warning: ";
  @NonNls private static final String PERTINENT = "is not (any longer) pertinent";
  @NonNls private static final String MERGING = "Merging differences between ";
  @NonNls private static final Pattern NOT_IN_REPOSITORY_PATTERN = Pattern.compile("cvs .+: (.+) is no longer in the repository");
  @NonNls private static final String LOCALLY_MODIFIED_FILE_HAS_BEEN_REMOVED = "is locally modified, but has been removed in revision";

  // Fields =================================================================

  private final IEventSender eventManager;
  private final ICvsFileSystem cvsFileSystem;
  private UpdateFileInfo fileInfo;

  // Setup ==================================================================

  public UpdateMessageParser(IEventSender eventManager, ICvsFileSystem cvsFileSystem) {
    BugLog.getInstance().assertNotNull(eventManager);
    BugLog.getInstance().assertNotNull(cvsFileSystem);

    this.eventManager = eventManager;
    this.cvsFileSystem = cvsFileSystem;
  }

  // Implemented ============================================================

  @Override
  public void registerListeners(ICvsListenerRegistry listenerRegistry) {
    super.registerListeners(listenerRegistry);
    listenerRegistry.addEntryListener(this);
  }

  @Override
  public void unregisterListeners(ICvsListenerRegistry listenerRegistry) {
    listenerRegistry.removeEntryListener(this);
    super.unregisterListeners(listenerRegistry);
  }

  @Override
  protected void outputDone() {
    if (fileInfo == null) {
      return;
    }

    // There can be a null value, if only the entry changed
    if (fileInfo.getType() != null) {
      eventManager.notifyFileInfoListeners(fileInfo);
    }
    fileInfo = null;
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void parseLine(String line, boolean isErrorMessage) {
    if (line.startsWith("RCS file:")) {
      return;
    }
    if (line.startsWith("retrieving revision ")) {
      return;
    }

    if (line.indexOf(" already contains the differences between ") > 0) {
      return;
    }

    if (line.startsWith("rcsmerge: warning: conflicts during merge")) {
      return;
    }

    if (line.contains(EXAM_DIR)) {
      return;
    }

    if (line.indexOf(": conflicts found in ") > 0) {
      return;
    }

    int index = line.indexOf(UNKNOWN);
    if (index >= 0) {
      final String fileName = (line.substring(index + UNKNOWN.length())).trim();
      processUnknown(fileName);
      return;
    }

    index = line.indexOf(TO_ADD);
    if (index >= 0) {
      final String fileName = (line.substring(index + TO_ADD.length())).trim();
      processUnknown(fileName);
      return;
    }

    if (line.startsWith(MERGING)) {
//			outputDone();
//			ensureExistingFileInfoContainer();
//			index = line.indexOf(UpdateCommand2.INTO, UpdateCommand2.MERGING.length() + 1);
//			if (index > 0) {
//				fileInfoContainer.setFile(createFile(line.substring(index + UpdateCommand2.INTO.length())));
//			}
//			fileInfoContainer.setType(DefaultFileInfoContainer.MERGED_FILE);
      return;
    }

    index = line.indexOf(LOCALLY_MODIFIED_FILE_HAS_BEEN_REMOVED);
    if (index >= 0) {
      String warningPrefix = "cvs server: file ";
      if (!line.startsWith(warningPrefix)) return;
      final String fileName = line.substring(warningPrefix.length(), index).trim();
      final FileObject fileObject = cvsFileSystem.unixFileNameToFileObject(fileName);
      ensureExistingFileInfoContainer(fileObject);
      // HACK - will create conflict status in order to be able to have consistent info format
      fileInfo.setType("C");
      return;
    }

    index = line.indexOf(WARNING);
    if (index >= 0) {
      final int pertinentIndex = line.indexOf(PERTINENT);
      if (pertinentIndex > 0) {
        final String fileName = line.substring(index + WARNING.length(), pertinentIndex).trim();
        final FileObject fileObject = cvsFileSystem.unixFileNameToFileObject(fileName);
        processNotPertinent(fileObject);
      }
      return;
    }

    Matcher m = NOT_IN_REPOSITORY_PATTERN.matcher(line);
    if (m.matches()) {
      final String fileName = m.group(1).trim();
      final FileObject fileObject = cvsFileSystem.unixFileNameToFileObject(fileName);
      processNotPertinent(fileObject);
      return;
    }

    // otherwise
    if (line.length() > 2) {
      if (line.charAt(1) == ' ') {
        final String firstChar = line.substring(0, 1);
        if (STATES.contains(firstChar)) {
          processFile(line.substring(2), firstChar);
        }
      }
    }
  }

  @Override
  public void gotEntry(FileObject fileObject, Entry entry) {
    ensureExistingFileInfoContainer(fileObject);
    fileInfo.setEntry(entry);
  }

  // Utils ==================================================================

  private void ensureExistingFileInfoContainer(FileObject fileObject) {
    if (fileInfo != null) {
      if (fileObject.equals(fileInfo.getFileObject())) {
        return;
      }

      outputDone();
    }

    fileInfo = new UpdateFileInfo(fileObject, cvsFileSystem.getLocalFileSystem().getFile(fileObject));
  }

  private void processUnknown(String fileName) {
    final FileObject fileObject = cvsFileSystem.unixFileNameToFileObject(fileName);
    ensureExistingFileInfoContainer(fileObject);
    fileInfo.setType("?");
  }

  private void processFile(String fileName, String type) {
    final FileObject fileObject = cvsFileSystem.unixFileNameToFileObject(fileName);
    ensureExistingFileInfoContainer(fileObject);
    fileInfo.setType(type);
  }

  private void processNotPertinent(FileObject fileObject) {
    ensureExistingFileInfoContainer(fileObject);

    // HACK - will create a non-cvs status in order to be able to have consistent info format
    fileInfo.setType("Y");
  }
}

