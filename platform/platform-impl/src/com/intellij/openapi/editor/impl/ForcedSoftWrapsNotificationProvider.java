// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ForcedSoftWrapsNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("forced.soft.wraps.notification.panel");
  private static final String DISABLED_NOTIFICATION_KEY = "disable.forced.soft.wraps.notification";

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull final FileEditor fileEditor, @NotNull Project project) {
    if (!(fileEditor instanceof TextEditor)) return null;
    final Editor editor = ((TextEditor)fileEditor).getEditor();
    if (!Boolean.TRUE.equals(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS)) ||
        !Boolean.TRUE.equals(editor.getUserData(EditorImpl.SOFT_WRAPS_EXIST)) ||
        PropertiesComponent.getInstance().isTrueValue(DISABLED_NOTIFICATION_KEY)) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);
    panel.setText(EditorBundle.message("forced.soft.wrap.message"));
    panel.createActionLabel(EditorBundle.message("notification.hide.message"), () -> {
      editor.putUserData(EditorImpl.FORCED_SOFT_WRAPS, null);
      EditorNotifications.getInstance(project).updateNotifications(file);
    });
    panel.createActionLabel(EditorBundle.message("notification.dont.show.again.message"), () -> {
      PropertiesComponent.getInstance().setValue(DISABLED_NOTIFICATION_KEY, "true");
      EditorNotifications.getInstance(project).updateAllNotifications();
    });
    return panel;
  }
}
