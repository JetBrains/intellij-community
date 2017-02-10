/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvshandlers.CvsMessagePattern;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.update.UpdateFileInfo;
import org.netbeans.lib.cvsclient.command.update.UpdatedFileInfo;
import org.netbeans.lib.cvsclient.event.EventManager;
import org.netbeans.lib.cvsclient.event.IEntryListener;
import org.netbeans.lib.cvsclient.event.IFileInfoListener;
import org.netbeans.lib.cvsclient.event.IMessageListener;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * author: lesya
 */
public class CvsMessagesTranslator implements IFileInfoListener, IMessageListener, IEntryListener {

  @NonNls private static final CvsMessagePattern[] ERRORS_PATTERNS = new CvsMessagePattern[]{
    new CvsMessagePattern("cvs [* aborted]*"),
    new CvsMessagePattern("Usage: cvs* server*"),
    new CvsMessagePattern("cvs*: invalid option*"),
    new CvsMessagePattern(new String[]{"cvs checkout: could not check out ", "*"}, 2),
    new CvsMessagePattern("cvs* update: could not merge revision * of *: No such file or directory"),
    new CvsMessagePattern(new String[]{"cvs* update: could not patch ", "*", "; will refetch"}, 2),
    new CvsMessagePattern("dying gasps from * unexpected"),
    new CvsMessagePattern("end of file from server (consult above messages if any)"),
    new CvsMessagePattern("\\*PANIC\\* administration files missing"),
    new CvsMessagePattern(new String[]{"cvs*: Up-to-date check failed for `", "*", "'"}, 2),
    new CvsMessagePattern("cvs*: cannot add file on non-branch tag *"),
    new CvsMessagePattern("Cannot access *"),
    new CvsMessagePattern("error  Permission denied"),
    new CvsMessagePattern(new String[]{"cvs*: ", "*", " already exists, with version number *"}, 2),
    new CvsMessagePattern(new String[]{"cvs*: cannot commit with sticky date for file `", "*", "'"}, 2),
    new CvsMessagePattern(new String[]{"cvs*: nothing known about `", "*", "'"}, 2),
    new CvsMessagePattern("cvs*: sticky tag `*' for file `*' is not a branch"),
    new CvsMessagePattern("cvs*: ERROR: cannot mkdir * -- not added: No such file or directory"),
    new CvsMessagePattern(new String[]{"cvs: nothing known about ", "*"}, 2),
    new CvsMessagePattern("Root * must be an absolute pathname"),
    new CvsMessagePattern("protocol error: *"),
    new CvsMessagePattern("cvs* tag: nothing known about *"),
    new CvsMessagePattern("cvs* tag: cannot*"),
    new CvsMessagePattern("cvs* tag:* failed*"),
    new CvsMessagePattern("cvs* tag: Not removing branch tag `*' from `*,v'."),
    new CvsMessagePattern("cvs tag: *: Not moving branch tag `*' from * to *."),
    new CvsMessagePattern(new String[]{"cvs*: failed to create lock directory for `", "*", "' (*/#cvs.lock): No such file or directory"}, 2)
  };

  private static final CvsMessagePattern[] WARNINGS_PATTERNS = new CvsMessagePattern[]{
    new CvsMessagePattern("cvs server: cannot open *: Permission denied"),
    new CvsMessagePattern("cvs server: cannot make path to *: Permission denied"),
    new CvsMessagePattern("cvs server: cannot find module `*' - ignored"),
    new CvsMessagePattern("W * : * already exists on version * : NOT MOVING tag to version *"),
    new CvsMessagePattern("W * : * already exists on branch * : NOT MOVING tag to branch *"),
    new CvsMessagePattern(new String[]{"cvs server: ", "*", " added independently by second party"}, 2),
    new CvsMessagePattern("cvs server: failed to create lock directory for `*' (*#cvs.lock): No such file or directory"),
    new CvsMessagePattern("cvs server: failed to obtain dir lock in repository `*'"),
    new CvsMessagePattern("cvs*: warning: *")
  };

  private static final Pattern[] FILE_MESSAGE_PATTERNS = new Pattern[]{PatternUtil.fromMask("cvs*: Updating*"),
    PatternUtil.fromMask("Directory * added to the repository"), PatternUtil.fromMask("cvs*: scheduling file `*' for addition"),
    PatternUtil.fromMask("cvs *: [*] waiting for *'s lock in *"), PatternUtil.fromMask("RCS file: *,v"),
    PatternUtil.fromMask("cvs*: Tagging*"), PatternUtil.fromMask("cvs add: scheduling file `*' for addition"),
    PatternUtil.fromMask("cvs rlog: Logging*")
  };

  private static final Pattern[] MESSAGE_PATTERNS = new Pattern[]{PatternUtil.fromMask("cvs *: [*] waiting for *'s lock in *")};

  private final CvsMessagesListener myListener;
  private final ICvsFileSystem myCvsFileSystem;
  private final UpdatedFilesManager myUpdatedFilesManager;
  private final String myCvsRoot;
  private final Map<File, Object> myFileToInfoMap = new HashMap<>();

  private final Collection<String> myPreviousErrorMessages = new ArrayList<>();
  @NonNls private static final String CORRECT_ABOVE_ERRORS_FIRST_PREFIX = "correct above errors first";

  enum MessageType { MESSAGE, FILE_MESSAGE, WARNING, ERROR }
  private MessageType lastMessage = null;

  public CvsMessagesTranslator(CvsMessagesListener listener,
                               ICvsFileSystem cvsFileSystem,
                               UpdatedFilesManager mergedFilesCollector,
                               String cvsroot) {
    myListener = listener;
    myCvsFileSystem = cvsFileSystem;
    myUpdatedFilesManager = mergedFilesCollector;
    myCvsRoot = cvsroot;
  }

  public void fileInfoGenerated(Object info) {
    if (info instanceof UpdateFileInfo) {
      final UpdateFileInfo updateFileInfo = (UpdateFileInfo)info;
      final File file = updateFileInfo.getFile();
      if (!myUpdatedFilesManager.fileIsNotUpdated(file) && !myFileToInfoMap.containsKey(file)) {
        myFileToInfoMap.put(file, updateFileInfo);
      }
    }
    else if (info instanceof UpdatedFileInfo) {
      final UpdatedFileInfo updatedFileInfo = (UpdatedFileInfo)info;
      final File file = updatedFileInfo.getFile();
      if (!myUpdatedFilesManager.fileIsNotUpdated(file)) {
        myFileToInfoMap.put(file, updatedFileInfo);
      }
    }
  }

  public void gotEntry(FileObject fileObject, Entry entry) {}

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    myListener.addMessage(new MessageEvent(message, error, tagged));
    if (message.isEmpty()) {
      return;
    }

    if (isFileMessage(message)) {
      lastMessage = MessageType.FILE_MESSAGE;
      myListener.addFileMessage(message, myCvsFileSystem);
      return;
    }
    if (isMessage(message)) {
      lastMessage = MessageType.MESSAGE;
      myListener.addMessage(message);
      return;
    }

    final CvsMessagePattern errorMessagePattern = getErrorMessagePattern(message, ERRORS_PATTERNS);
    if (errorMessagePattern != null) {
      if (message.contains(CORRECT_ABOVE_ERRORS_FIRST_PREFIX)) {
        for (String s : myPreviousErrorMessages) {
          myListener.addError(s, null, myCvsFileSystem, myCvsRoot, false);
        }
        myPreviousErrorMessages.clear();
        return;
      }
      final String relativeFileName = errorMessagePattern.getRelativeFileName(message);
      lastMessage = MessageType.ERROR;
      myListener.addError(message, relativeFileName, myCvsFileSystem, myCvsRoot, false);
      return;
    }
    final CvsMessagePattern warningMessagePattern = getErrorMessagePattern(message, WARNINGS_PATTERNS);
    if (warningMessagePattern != null) {
      lastMessage = MessageType.WARNING;
      myListener.addError(message,warningMessagePattern.getRelativeFileName(message), myCvsFileSystem, myCvsRoot, true);
      return;
    }

    if (message.trim().isEmpty()) return;
    if (lastMessage == MessageType.ERROR) {
      myListener.addError(message, null, myCvsFileSystem, myCvsRoot, false);
    } else if (lastMessage == MessageType.WARNING) {
      myListener.addError(message, null, myCvsFileSystem, myCvsRoot, true);
    } else if (error) {
      myPreviousErrorMessages.add(message);
    }
  }

  private static boolean isFileMessage(String message) {
    for (Pattern pattern : FILE_MESSAGE_PATTERNS) {
      if (pattern.matcher(message).matches()) return true;
    }
    return false;
  }

  private static boolean isMessage(String message) {
    for (Pattern pattern : MESSAGE_PATTERNS) {
      if (pattern.matcher(message).matches()) return true;
    }
    return false;
  }

  @Nullable
  private static CvsMessagePattern getErrorMessagePattern(String message, CvsMessagePattern[] patterns) {
    for (CvsMessagePattern pattern : patterns) {
      if (pattern.matches(message)) {
        return pattern;
      }
    }
    return null;
  }

  public void registerTo(EventManager eventManager) {
    eventManager.addFileInfoListener(this);
    eventManager.addMessageListener(this);
  }

  public void operationCompleted() {
    if (!CvsEntriesManager.getInstance().isActive()) {
      return;
    }
    for (File file : myFileToInfoMap.keySet()) {
      addFileMessage(myFileToInfoMap.get(file), file);
    }
  }

  private void addFileMessage(Object info, File file) {
    if (info instanceof UpdateFileInfo) {
      addUpdateFileInfo(file, (UpdateFileInfo)info);
    }
    else if (info instanceof UpdatedFileInfo) {
      addUpdatedFileInfo(file, (UpdatedFileInfo)info);
    }
  }

  private void addUpdatedFileInfo(File file, UpdatedFileInfo updatedFileInfo) {
    if (!myUpdatedFilesManager.fileIsNotUpdated(file)) {
      myListener.addFileMessage(new FileMessage(updatedFileInfo, myUpdatedFilesManager));
    }
  }

  private void addUpdateFileInfo(File file, UpdateFileInfo updateFileInfo) {
    if (!myUpdatedFilesManager.fileIsNotUpdated(file)) {
      myListener.addFileMessage(new FileMessage(updateFileInfo, myUpdatedFilesManager, myUpdatedFilesManager));
    }
  }

  public void binaryMessageSent(final byte[] bytes) {}
}