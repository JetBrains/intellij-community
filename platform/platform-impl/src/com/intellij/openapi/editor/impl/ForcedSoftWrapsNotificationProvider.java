// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.AppMode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.function.Function;

final class ForcedSoftWrapsNotificationProvider implements EditorNotificationProvider, DumbAware {
  private static final String DISABLED_NOTIFICATION_KEY = "disable.forced.soft.wraps.notification";

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    // In split mode the frontend renders the editor and shows this banner itself, while the host's copy is projected on top of it,
    // so the banner appears twice (IJPL-167724). Hiding the host's copy wouldn't affect the rendered editor's soft wraps anyway.
    if (AppMode.isRemoteDevHost()) return null;

    return fileEditor -> {
      if (!(fileEditor instanceof TextEditor te)) return null;
      final Editor editor = te.getEditor();
      if (!Boolean.TRUE.equals(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS)) ||
          !Boolean.TRUE.equals(editor.getUserData(EditorImpl.SOFT_WRAPS_EXIST)) ||
          PropertiesComponent.getInstance().isTrueValue(DISABLED_NOTIFICATION_KEY)) return null;

      final EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
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
    };
  }
}
