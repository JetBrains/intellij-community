package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.update.UpdateFileInfo;
import org.netbeans.lib.cvsclient.command.update.UpdatedFileInfo;

import java.io.File;

public class FileMessage {
  public static final int UNKNOWN = -1;
  public static final int SCHEDULING_FOR_ADDING = 0;
  public static final int SCHEDULING_FOR_REMOVING = 1;
  public static final int UPDATING = 2;
  public static final int IMPORTING = 3;
  public static final int ADDING = 4;
  public static final int REMOVING = 5;
  public static final int SAVING = 6;
  public static final int SENDING = 7;
  public static final int MODIFIED = 8;
  public static final int MERGED_WITH_CONFLICTS = 9;
  public static final int NOT_IN_REPOSITORY = 10;
  public static final int LOCALLY_ADDED = 11;
  public static final int LOCALLY_REMOVED = 12;
  public static final int REMOVED_FROM_REPOSITORY = 13;
  public static final int PATCHED = 14;
  public static final int UPDATING2 = 15;
  public static final int MERGED = 16;
  public static final int REMOVED_FROM_SERVER_CONFLICT = 17;
  public static final int LOCALLY_REMOVED_CONFLICT = 18;
  public static final int CREATED = 19;
  public static final int CREATED_BY_SECOND_PARTY = 20;

  private int myType;
  private String myFileAbsolutePath = "";
  public static final String CONFLICT = "C";


  public FileMessage(UpdateFileInfo info,
                     UpdatedFilesManager mergedFilesCollector,
                     UpdatedFilesManager updatedFilesManager) {
    myType = getCommitOperationType(info.getType(),
                                    info.getFile(),
                                    info.getEntry(),
                                    mergedFilesCollector,
                                    updatedFilesManager);
    myFileAbsolutePath = info.getFile().getAbsolutePath();
  }

  public FileMessage(UpdatedFileInfo info,
                     UpdatedFilesManager updatedFilesManager) {
    myType = getUpdateOperationType(info.getType(), info.getFile(), updatedFilesManager, info.getEntry());
    myFileAbsolutePath = info.getFile().getAbsolutePath();
  }

  public void showMessageIn(ProgressIndicator progress) {
    progress.setText2(getMyActionName() + ": " + myFileAbsolutePath);
  }

  private String getMyActionName() {
    switch (myType) {
      case SCHEDULING_FOR_ADDING:
        return "Scheduling for adding";
      case SCHEDULING_FOR_REMOVING:
        return "Scheduling for removing";
      case UPDATING:
        return "Updating";
      case UPDATING2:
        return "Updating";
      case IMPORTING:
        return "Importing";
      case ADDING:
        return "Adding";
      case REMOVING:
        return "Removing";
      case SAVING:
        return "Saving";
      case SENDING:
        return "Sending";
      case MODIFIED:
        return "Modified";

    }

    return "Processing ";

  }

  public String getFileAbsolutePath() {
    return myFileAbsolutePath;
  }

  public int getType() {
    return myType;
  }

  private int getUpdateOperationType(UpdatedFileInfo.UpdatedType type,
                                     File file,
                                     UpdatedFilesManager updatedFilesManager, Entry entry) {
    if (type == UpdatedFileInfo.UpdatedType.REMOVED) {
      return REMOVED_FROM_REPOSITORY;
    }
    VirtualFile virtualParent = CvsVfsUtil.getParentFor(file);
    if (virtualParent == null) return CREATED;
    if (type == UpdatedFileInfo.UpdatedType.MERGED) {
      if (entry.isConflict()) {
        return MERGED_WITH_CONFLICTS;
      }
      else {
        return MERGED;
      }
    }
    else {
      if (updatedFilesManager.isNewlyCreatedEntryFor(virtualParent, file.getName())) {
        return CREATED;
      }

      return UPDATING;
    }
  }


  private static int getCommitOperationType(String commitOperationType,
                                            File file,
                                            Entry entry,
                                            UpdatedFilesManager mergedFiles,
                                            UpdatedFilesManager updatedFilesManager) {
    if (commitOperationType.equals("U")) {
      VirtualFile virtualParent = CvsVfsUtil.getParentFor(file);
      if (virtualParent == null) return CREATED;
      if (updatedFilesManager.isNewlyCreatedEntryFor(virtualParent, file.getName())) {
        return CREATED;
      }
      return UPDATING;
    }
    else if (commitOperationType.equals("P")) {
      return PATCHED;
    }
    else if (commitOperationType.equals("A")) {
      return LOCALLY_ADDED;
    }
    else if (commitOperationType.equals("R")) {
      return LOCALLY_REMOVED;
    }
    else if (commitOperationType.equals("M")) {
      if (mergedFiles.isMerged(file)) {
        return MERGED;
      }
      else {
        return MODIFIED;
      }
    }
    else if (commitOperationType.equals(CONFLICT)) {
      if ((entry != null && entry.isResultOfMerge()) || mergedFiles.isMerged(file)) {
        return MERGED_WITH_CONFLICTS;
      }
      else if (mergedFiles.isCreatedBySecondParty(file)) {
        return CREATED_BY_SECOND_PARTY;
      }
      else if (CvsUtil.isLocallyRemoved(file)) {
        return LOCALLY_REMOVED_CONFLICT;
      }
      else {
        return REMOVED_FROM_SERVER_CONFLICT;
      }
    }
    else if (commitOperationType.equals("Y")) {
      return REMOVED_FROM_REPOSITORY;
    }
    else if (commitOperationType.equals("?")) {
      return NOT_IN_REPOSITORY;
    }
    else {
      return UNKNOWN;
    }
  }
}
