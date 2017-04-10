/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl.text;

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

public class LargeFileNotificationProvider extends EditorNotifications.Provider {
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
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!(fileEditor instanceof LargeFileEditorProvider.LargeTextFileEditor)) return null;
    Editor editor = ((TextEditor)fileEditor).getEditor();
    Project project = editor.getProject();
    if (project == null || editor.getUserData(HIDDEN_KEY) != null || PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)) {
      return null;
    }

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.createActionLabel("Hide notification", () -> {
      editor.putUserData(HIDDEN_KEY, "true");
      update(file, project);
    });
    panel.createActionLabel("Don't show again", () -> {
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
