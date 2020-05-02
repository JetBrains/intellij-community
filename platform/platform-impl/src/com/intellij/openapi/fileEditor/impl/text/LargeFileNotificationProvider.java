// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LargeFileNotificationProvider extends EditorNotifications.Provider {
  private static final Key<EditorNotificationPanel> KEY = Key.create("large.file.editor.notification");
  private static final Key<String> HIDDEN_KEY = Key.create("large.file.editor.notification.hidden");
  private static final String DISABLE_KEY = "large.file.editor.notification.disabled";

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (!(fileEditor instanceof LargeFileEditorProvider.LargeTextFileEditor)) return null;
    Editor editor = ((TextEditor)fileEditor).getEditor();
    if (editor.getUserData(HIDDEN_KEY) != null || PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)) {
      return null;
    }

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.createActionLabel(IdeBundle.message("action.label.hide.notification"), () -> {
      editor.putUserData(HIDDEN_KEY, "true");
      update(file, project);
    });
    panel.createActionLabel(IdeBundle.message("label.dont.show"), () -> {
      PropertiesComponent.getInstance().setValue(DISABLE_KEY, "true");
      update(file, project);
    });
    return panel.text(String.format(
      "The file is too large: %s. Showing a read-only preview of the first %s.",
      StringUtil.formatFileSize(file.getLength()),
      StringUtil.formatFileSize(FileUtilRt.LARGE_FILE_PREVIEW_SIZE)
    ));
  }

  private static void update(@NotNull VirtualFile file, @NotNull Project project) {
    EditorNotifications.getInstance(project).updateNotifications(file);
  }
}
