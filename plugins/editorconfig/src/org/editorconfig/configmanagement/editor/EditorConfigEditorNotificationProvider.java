// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ui.EditorNotificationProvider;
import org.editorconfig.language.filetype.EditorConfigFileType;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;

final class EditorConfigEditorNotificationProvider implements EditorNotificationProvider<EditorNotificationPanel> {

  private static final Key<EditorNotificationPanel> KEY = Key.create("org.editorconfig.config.management.editor.notification.provider");

  private final Project myProject;

  EditorConfigEditorNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Key<EditorNotificationPanel> getKey() {
    return KEY;
  }


  @Override
  public @NotNull ComponentProvider<EditorNotificationPanel> collectNotificationData(@NotNull Project project,
                                                                                     @NotNull VirtualFile file) {
    return file.getFileType().equals(EditorConfigFileType.INSTANCE) &&
           !CodeStyle.getSettings(project).getCustomSettings(EditorConfigSettings.class).ENABLED ?
           MyPanel::new :
           ComponentProvider.getDummy();
  }

  private final class MyPanel extends EditorNotificationPanel {

    private MyPanel(@NotNull FileEditor fileEditor) {
      super(fileEditor);
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
