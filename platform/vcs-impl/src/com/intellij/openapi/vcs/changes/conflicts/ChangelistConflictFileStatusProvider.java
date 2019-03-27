// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.impl.FileStatusProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public final class ChangelistConflictFileStatusProvider implements FileStatusProvider {
  public static final FileStatus MODIFIED_OUTSIDE =
    FileStatusFactory.getInstance().createFileStatus("modifiedOutside", "Modified in not active changelist");
  public static final FileStatus ADDED_OUTSIDE =
    FileStatusFactory.getInstance().createFileStatus("addedOutside", "Added in not active changelist");
  public static final FileStatus CHANGELIST_CONFLICT =
    FileStatusFactory.getInstance().createFileStatus("changelistConflict", "Changelist conflict");

  private final ChangeListManagerImpl myChangeListManager;

  public ChangelistConflictFileStatusProvider(@NotNull Project project) {
    myChangeListManager = ChangeListManagerImpl.getInstanceImpl(project);
  }

  @Override
  @Nullable
  public FileStatus getFileStatus(@NotNull VirtualFile virtualFile) {
    ChangelistConflictTracker conflictTracker = myChangeListManager.getConflictTracker();
    ChangelistConflictTracker.Options options = conflictTracker.getOptions();
    if (options.HIGHLIGHT_CONFLICTS && conflictTracker.hasConflict(virtualFile)) {
      return CHANGELIST_CONFLICT;
    }
    else if (options.HIGHLIGHT_NON_ACTIVE_CHANGELIST) {
      FileStatus status = myChangeListManager.getStatus(virtualFile);
      if (status == FileStatus.MODIFIED || status == FileStatus.ADDED) {
        if (!conflictTracker.isFromActiveChangelist(virtualFile)) {
          return status == FileStatus.MODIFIED ? MODIFIED_OUTSIDE : ADDED_OUTSIDE;
        }
      }
    }
    return null;
  }

  @Override
  public void refreshFileStatusFromDocument(@NotNull VirtualFile virtualFile, @NotNull Document doc) {

  }

  @NotNull
  @Override
  public ThreeState getNotChangedDirectoryParentingStatus(@NotNull VirtualFile virtualFile) {
    throw new UnsupportedOperationException("Shouldn't be called");
  }
}
