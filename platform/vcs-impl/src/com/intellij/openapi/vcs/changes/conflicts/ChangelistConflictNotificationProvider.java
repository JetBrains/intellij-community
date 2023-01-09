// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

final class ChangelistConflictNotificationProvider implements EditorNotificationProvider, DumbAware {
  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    return ChangelistConflictTracker.getInstance(project).hasConflict(file)
           ? fileEditor -> ChangelistConflictNotificationPanel.create(project, file, fileEditor)
           : null;
  }
}
