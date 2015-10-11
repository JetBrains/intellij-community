/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.impl.FileStatusProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictFileStatusProvider implements FileStatusProvider {

  private static final FileStatus MODIFIED_OUTSIDE =
    FileStatusFactory.getInstance().createFileStatus("modifiedOutside", "Modified in not active changelist", FileStatus.COLOR_MODIFIED.brighter());
  private static final FileStatus ADDED_OUTSIDE =
    FileStatusFactory.getInstance().createFileStatus("addedOutside", "Added in not active changelist", FileStatus.COLOR_ADDED.brighter());
  private static final FileStatus CHANGELIST_CONFLICT =
    FileStatusFactory.getInstance().createFileStatus("changelistConflict", "Changelist conflict", JBColor.RED);

  private final ChangelistConflictTracker myConflictTracker;
  private final ChangeListManager myChangeListManager;

  public ChangelistConflictFileStatusProvider(ChangeListManagerImpl changeListManager) {
    myChangeListManager = changeListManager;
    myConflictTracker = changeListManager.getConflictTracker();
  }

  @Override
  @Nullable
  public FileStatus getFileStatus(@NotNull VirtualFile virtualFile) {
    ChangelistConflictTracker.Options options = myConflictTracker.getOptions();
    if (!options.TRACKING_ENABLED) {
      return null;
    }
    boolean conflict = myConflictTracker.hasConflict(virtualFile);
    if (conflict && options.HIGHLIGHT_CONFLICTS) {
      return CHANGELIST_CONFLICT;
    }
    else if (options.HIGHLIGHT_NON_ACTIVE_CHANGELIST) {
      FileStatus status = myChangeListManager.getStatus(virtualFile);
      if (status == FileStatus.MODIFIED || status == FileStatus.ADDED) {
        if (!myConflictTracker.isFromActiveChangelist(virtualFile)) {
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
