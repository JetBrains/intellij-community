// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.FileStatusProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public final class ChangelistConflictFileStatusProvider implements FileStatusProvider {
  public static final FileStatus MODIFIED_OUTSIDE = FileStatusFactory.getInstance()
    .createFileStatus("modifiedOutside", VcsBundle.messagePointer("settings.file.status.color.modified.in.not.active.changelist"), null);
  public static final FileStatus ADDED_OUTSIDE = FileStatusFactory.getInstance()
    .createFileStatus("addedOutside", VcsBundle.messagePointer("settings.file.status.color.added.in.not.active.changelist"), null);
  public static final FileStatus CHANGELIST_CONFLICT = FileStatusFactory.getInstance()
    .createFileStatus("changelistConflict", VcsBundle.messagePointer("settings.file.status.color.changelist.conflict"), null);

  private final Project myProject;

  public ChangelistConflictFileStatusProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @Nullable
  public FileStatus getFileStatus(@NotNull VirtualFile virtualFile) {
    ChangelistConflictTracker conflictTracker = ChangelistConflictTracker.getInstance(myProject);
    ChangelistConflictTracker.Options options = conflictTracker.getOptions();
    if (options.HIGHLIGHT_CONFLICTS && conflictTracker.hasConflict(virtualFile)) {
      return CHANGELIST_CONFLICT;
    }
    else if (options.HIGHLIGHT_NON_ACTIVE_CHANGELIST) {
      FileStatus status = ChangeListManager.getInstance(myProject).getStatus(virtualFile);
      if (status == FileStatus.MODIFIED || status == FileStatus.ADDED) {
        if (!conflictTracker.isFromActiveChangelist(virtualFile)) {
          return status == FileStatus.MODIFIED ? MODIFIED_OUTSIDE : ADDED_OUTSIDE;
        }
      }
    }
    return null;
  }
}
