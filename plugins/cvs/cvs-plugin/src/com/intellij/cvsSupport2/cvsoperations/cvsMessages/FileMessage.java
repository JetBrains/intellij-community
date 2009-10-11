/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.CvsBundle;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.update.UpdateFileInfo;
import org.netbeans.lib.cvsclient.command.update.UpdatedFileInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

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

  private final int myType;
  private String myFileAbsolutePath = "";
  private CvsRevisionNumber myRevision;
  @NonNls public static final String CONFLICT = "C";
  @NonNls private static final String U_COMMIT_OPERATION_TYPE = "U";
  @NonNls private static final String P_COMMIT_OPERATION_TYPE = "P";
  @NonNls private static final String A_COMMIT_OPERATION_TYPE = "A";
  @NonNls private static final String R_COMMIT_OPERATION_TYPE = "R";
  @NonNls private static final String M_COMMIT_OPERATION_TYPE = "M";
  @NonNls private static final String Y_COMMIT_OPERATION_TYPE = "Y";


  public FileMessage(UpdateFileInfo info,
                     UpdatedFilesManager mergedFilesCollector,
                     UpdatedFilesManager updatedFilesManager) {
    final Entry entry = info.getEntry();
    myType = getCommitOperationType(info.getType(),
                                    info.getFile(), entry,
                                    mergedFilesCollector,
                                    updatedFilesManager);
    myFileAbsolutePath = info.getFile().getAbsolutePath();
    if (entry != null) {
      myRevision = new CvsRevisionNumber(entry.getRevision());
    }
  }

  public FileMessage(UpdatedFileInfo info,
                     UpdatedFilesManager updatedFilesManager) {
    final Entry entry = info.getEntry();
    myType = getUpdateOperationType(info.getType(), info.getFile(), updatedFilesManager, entry);
    myFileAbsolutePath = info.getFile().getAbsolutePath();
    if (entry != null) {
      myRevision = new CvsRevisionNumber(entry.getRevision());
    }
  }

  public void showMessageIn(ProgressIndicator progress) {
    progress.setText2(getMyActionName() + ": " + myFileAbsolutePath);
  }

  private String getMyActionName() {
    switch (myType) {
      case SCHEDULING_FOR_ADDING:
        return CvsBundle.message("current.action.name.scheduling.for.adding");
      case SCHEDULING_FOR_REMOVING:
        return CvsBundle.message("current.action.name.scheduling.for.removing");
      case UPDATING:
        return CvsBundle.message("current.action.name.updating");
      case UPDATING2:
        return CvsBundle.message("current.action.name.updating");
      case IMPORTING:
        return CvsBundle.message("current.action.name.importing");
      case ADDING:
        return CvsBundle.message("current.action.name.adding");
      case REMOVING:
        return CvsBundle.message("current.action.name.removing");
      case SAVING:
        return CvsBundle.message("current.action.name.saving");
      case SENDING:
        return CvsBundle.message("current.action.name.sending");
      case MODIFIED:
        return CvsBundle.message("current.action.name.modified");

    }

    return CvsBundle.message("current.action.name.processing");

  }

  public String getFileAbsolutePath() {
    return myFileAbsolutePath;
  }

  public int getType() {
    return myType;
  }

  @Nullable
  public CvsRevisionNumber getRevision() {
    return myRevision;
  }

  private static int getUpdateOperationType(UpdatedFileInfo.UpdatedType type,
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
    if (commitOperationType.equals(U_COMMIT_OPERATION_TYPE)) {
      VirtualFile virtualParent = CvsVfsUtil.getParentFor(file);
      if (virtualParent == null) return CREATED;
      if (updatedFilesManager.isNewlyCreatedEntryFor(virtualParent, file.getName())) {
        return CREATED;
      }
      return UPDATING;
    }
    else if (commitOperationType.equals(P_COMMIT_OPERATION_TYPE)) {
      return PATCHED;
    }
    else if (commitOperationType.equals(A_COMMIT_OPERATION_TYPE)) {
      return LOCALLY_ADDED;
    }
    else if (commitOperationType.equals(R_COMMIT_OPERATION_TYPE)) {
      return LOCALLY_REMOVED;
    }
    else if (commitOperationType.equals(M_COMMIT_OPERATION_TYPE)) {
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
    else if (commitOperationType.equals(Y_COMMIT_OPERATION_TYPE)) {
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
