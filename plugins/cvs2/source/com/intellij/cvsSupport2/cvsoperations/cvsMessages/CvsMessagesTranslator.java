package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import com.intellij.cvsSupport2.cvshandlers.CvsMessagePattern;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.HashMap;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.update.UpdateFileInfo;
import org.netbeans.lib.cvsclient.command.update.UpdatedFileInfo;
import org.netbeans.lib.cvsclient.event.EventManager;
import org.netbeans.lib.cvsclient.event.IEntryListener;
import org.netbeans.lib.cvsclient.event.IFileInfoListener;
import org.netbeans.lib.cvsclient.event.IMessageListener;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * author: lesya
 */
public class CvsMessagesTranslator implements
                                   IFileInfoListener,
                                   IMessageListener,
                                   IEntryListener {

  private static CvsMessagePattern[] ERRORS_PATTERNS = new CvsMessagePattern[]{
    new CvsMessagePattern("cvs [* aborted]*"),
    new CvsMessagePattern(new String[]{"cvs checkout: could not check out ", "*"}, 2),
    new CvsMessagePattern("cvs update: could not merge revision * of *: No such file or directory"),
    new CvsMessagePattern(new String[]{"cvs update: could not patch ", "*", "; will refetch"}, 2),
    new CvsMessagePattern("dying gasps from * unexpected"),
    new CvsMessagePattern("end of file from server (consult above messages if any)"),
    new CvsMessagePattern("\\*PANIC\\* administration files missing"),
    new CvsMessagePattern(new String[]{"cvs *: Up-to-date check failed for `", "*", "'"}, 2),
    new CvsMessagePattern("cvs server: cannot add file on non-branch tag *"),
    new CvsMessagePattern("Cannot access *"),
    new CvsMessagePattern("error  Permission denied"),
    new CvsMessagePattern(new String[]{"cvs server: ", "*", " already exists, with version number *"}, 2),
    new CvsMessagePattern(new String[]{"cvs server: cannot commitAssertingNoCircularDependency with sticky date for file `", "*", "'"}, 2),
    new CvsMessagePattern(new String[]{"cvs server: nothing known about `", "*", "'"}, 2),
    new CvsMessagePattern("cvs server: sticky tag `" + "*" + "' for file `" + "*" + "' is not a branch"),
    new CvsMessagePattern("cvs server: ERROR: cannot mkdir * -- not added: No such file or directory"),
    new CvsMessagePattern(new String[]{"cvs server: nothing known about ", "*"}, 2),
    new CvsMessagePattern("Root * must be an absolute pathname"),
    new CvsMessagePattern("protocol error: *"),
    new CvsMessagePattern("cvs tag: nothing known about *")
  };

  private static CvsMessagePattern[] WARNINGS_PATTERNS = new CvsMessagePattern[]{
    new CvsMessagePattern("cvs server: cannot open *: mismission denied"),
    new CvsMessagePattern("cvs server: cannot make path to *: Permission denied"),
    new CvsMessagePattern("cvs server: cannot find module `*' - ignored"),
    new CvsMessagePattern("W * : * already exists on version * : NOT MOVING tag to version *"),
    new CvsMessagePattern(new String[]{"cvs server: ", "*", " added independently by second party"}, 2),
    new CvsMessagePattern("cvs server: failed to create lock directory for `*' (*#cvs.lock): No such file or directory"),
    new CvsMessagePattern("cvs server: failed to obtain dir lock in repository `*'")
  };

  private static Pattern[] FILE_MESSAGE_PATTERNS = new Pattern[]{
    PatternUtil.fromMask("cvs server: Updating*"),
    PatternUtil.fromMask("Directory * added to the repository"),
    PatternUtil.fromMask("cvs server: scheduling file `*' for addition"),
    PatternUtil.fromMask("cvs *: [*] waiting for *'s lock in *"),
    PatternUtil.fromMask("RCS file: *,v"),
    PatternUtil.fromMask("cvs server: Tagging*"),
    PatternUtil.fromMask("cvs add: scheduling file `*' for addition")
  };

  private final CvsMessagesListener myListener;
  private final ICvsFileSystem myCvsFileSystem;
  private final UpdatedFilesManager myUpdatedFilesManager;
  private final String myCvsRoot;
  private final Map<File, Object> myFileToInfoMap = new HashMap<File, Object>();

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
      UpdateFileInfo updateFileInfo = (UpdateFileInfo)info;
      File file = updateFileInfo.getFile();
      if (!myUpdatedFilesManager.fileIsNotUpdated(file) && !myFileToInfoMap.containsKey(file)) {
        myFileToInfoMap.put(file, updateFileInfo);
      }
    }
    else if (info instanceof UpdatedFileInfo) {
      UpdatedFileInfo updatedFileInfo = ((UpdatedFileInfo)info);
      File file = updatedFileInfo.getFile();
      if (!myUpdatedFilesManager.fileIsNotUpdated(file)) {
        myFileToInfoMap.put(file, updatedFileInfo);
      }
    }
  }

  public void gotEntry(FileObject fileObject, Entry entry) {

  }

  public void messageSent(String message, boolean error, boolean tagged) {
    myListener.addMessage(new MessageEvent(message, error, tagged));
    if (message.length() == 0) return;

    if (isFileMessage(message)) {
      myListener.addFileMessage(message, myCvsFileSystem);
      return;
    }

    CvsMessagePattern errorMessagePattern = getErrorMessagePattern(message, ERRORS_PATTERNS);
    if (errorMessagePattern != null) {
      myListener.addError(message, errorMessagePattern.getRelativeFileName(message), myCvsFileSystem, myCvsRoot);
      return;
    }
    CvsMessagePattern warningMessagePattern = getErrorMessagePattern(message, WARNINGS_PATTERNS);
    if (warningMessagePattern != null) {
      myListener.addWarning(message, warningMessagePattern.getRelativeFileName(message), myCvsFileSystem, myCvsRoot);
      return;
    }

  }

  private boolean isFileMessage(String message) {
    for (int i = 0; i < FILE_MESSAGE_PATTERNS.length; i++) {
      Pattern pattern = FILE_MESSAGE_PATTERNS[i];
      if (pattern.matcher(message).matches()) return true;
    }
    return false;
  }


  private CvsMessagePattern getErrorMessagePattern(String message, CvsMessagePattern[] patterns) {
    for (int i = 0; i < patterns.length; i++) {
      if (patterns[i].matches(message)) return patterns[i];
    }
    return null;
  }

  public void registerTo(EventManager eventManager) {
    eventManager.addFileInfoListener(this);
    eventManager.addMessageListener(this);
  }

  public void operationCompleted() {
    if (!CvsEntriesManager.getInstance().isActive()) return;
    for (Iterator<File> iterator = myFileToInfoMap.keySet().iterator(); iterator.hasNext();) {
      File file = iterator.next();
      addFileMessage(myFileToInfoMap.get(file), file);
    }
  }

  private void addFileMessage(Object info, File file) {
    if (info instanceof UpdateFileInfo) {
      addUpdateFileInfo(file, (UpdateFileInfo)info);
    }
    else if (info instanceof UpdatedFileInfo) {
      addUpdatedFileInfo(file, ((UpdatedFileInfo)info));
    }
  }

  private void addUpdatedFileInfo(File file, UpdatedFileInfo updatedFileInfo) {
    if (!myUpdatedFilesManager.fileIsNotUpdated(file)) {
      myListener.addFileMessage(new FileMessage(updatedFileInfo,
                                                myUpdatedFilesManager));

    }
  }

  private void addUpdateFileInfo(File file, UpdateFileInfo updateFileInfo) {
    if (!myUpdatedFilesManager.fileIsNotUpdated(file)) {
      myListener.addFileMessage(new FileMessage(updateFileInfo,
                                                myUpdatedFilesManager,
                                                myUpdatedFilesManager));
    }
  }
}
