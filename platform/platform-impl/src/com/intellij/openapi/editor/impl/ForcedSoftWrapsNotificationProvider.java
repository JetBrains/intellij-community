/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

public class ForcedSoftWrapsNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("forced.soft.wraps.notification.panel");
  private static final String DISABLED_NOTIFICATION_KEY = "disable.forced.soft.wraps.notification";

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull final FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) return null;
    final Editor editor = ((TextEditor)fileEditor).getEditor();
    final Project project = editor.getProject();
    if (project == null 
        || !Boolean.TRUE.equals(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS)) 
        || PropertiesComponent.getInstance().isTrueValue(DISABLED_NOTIFICATION_KEY)) return null;
    
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(EditorBundle.message("forced.soft.wrap.message"));
    panel.createActionLabel(EditorBundle.message("forced.soft.wrap.hide.message"), new Runnable() {
      @Override
      public void run() {
        editor.putUserData(EditorImpl.FORCED_SOFT_WRAPS, null);
        EditorNotifications.getInstance(project).updateNotifications(file);
      }
    });
    panel.createActionLabel(EditorBundle.message("forced.soft.wrap.dont.show.again.message"), new Runnable() {
      @Override
      public void run() {
        PropertiesComponent.getInstance().setValue(DISABLED_NOTIFICATION_KEY, "true");
        EditorNotifications.getInstance(project).updateAllNotifications();
      }
    });
    return panel;
  }
}
