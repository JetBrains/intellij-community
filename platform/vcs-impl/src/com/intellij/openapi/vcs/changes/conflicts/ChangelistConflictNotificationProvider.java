// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public final class ChangelistConflictNotificationProvider extends EditorNotifications.Provider<ChangelistConflictNotificationPanel> implements DumbAware {
  private static final Key<ChangelistConflictNotificationPanel> KEY = Key.create("changelistConflicts");

  @Override
  @NotNull
  public Key<ChangelistConflictNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public ChangelistConflictNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    ChangelistConflictTracker conflictTracker = ChangelistConflictTracker.getInstance(project);
    return conflictTracker.hasConflict(file) ? ChangelistConflictNotificationPanel.create(conflictTracker, file, fileEditor) : null;
  }
}
