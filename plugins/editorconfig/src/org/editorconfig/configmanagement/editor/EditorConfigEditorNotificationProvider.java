// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.editorconfig.language.filetype.EditorConfigFileType;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EditorConfigEditorNotificationProvider extends EditorNotifications.Provider<JPanel> {
  private static final Key<JPanel> KEY = Key.create("org.editorconfig.config.management.editor.notification.provider");

  private final Project myProject;

  public EditorConfigEditorNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Key<JPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public JPanel createNotificationPanel(@NotNull VirtualFile file,
                                        @NotNull FileEditor fileEditor,
                                        @NotNull Project project) {
    if (file.getFileType().equals(EditorConfigFileType.INSTANCE)) {
      if (!CodeStyle.getSettings(project).getCustomSettings(EditorConfigSettings.class).ENABLED) {
        return new MyPanel();
      }
    }
    return null;
  }

  private final class MyPanel extends EditorNotificationPanel {
    private MyPanel() {
      setText(EditorConfigBundle.message("editor.notification.disabled"));

      createActionLabel(EditorConfigBundle.message("editor.notification.enable"), () -> {
        CodeStyle.getSettings(myProject).getCustomSettings(EditorConfigSettings.class).ENABLED = true;
        CodeStyleSettingsManager.getInstance(myProject).notifyCodeStyleSettingsChanged();
      });

      createActionLabel(EditorConfigBundle.message("editor.notification.open.settings"), () -> {
        ShowSettingsUtil.getInstance().showSettingsDialog(myProject, IdeBundle.message("configurable.CodeStyle.display.name"));
      });
    }
  }
}
