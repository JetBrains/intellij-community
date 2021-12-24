// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.editorconfig.language.filetype.EditorConfigFileType;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

final class EditorConfigEditorNotificationProvider implements EditorNotificationProvider {

  @Override
  public @NotNull Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                @NotNull VirtualFile file) {
    return file.getFileType().equals(EditorConfigFileType.INSTANCE) &&
           !getEditorConfigSettings(project).ENABLED ?
           fileEditor -> new MyPanel(fileEditor, project) :
           CONST_NULL;
  }

  private static final class MyPanel extends EditorNotificationPanel {

    private MyPanel(@NotNull FileEditor fileEditor,
                    @NotNull Project project) {
      super(fileEditor);
      setText(EditorConfigBundle.message("editor.notification.disabled"));

      createActionLabel(EditorConfigBundle.message("editor.notification.enable"), () -> {
        getEditorConfigSettings(project).ENABLED = true;
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged();
      });

      createActionLabel(EditorConfigBundle.message("editor.notification.open.settings"), () -> {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, IdeBundle.message("configurable.CodeStyle.display.name"));
      });
    }
  }

  private static @NotNull EditorConfigSettings getEditorConfigSettings(@NotNull Project project) {
    return CodeStyle.getSettings(project).getCustomSettings(EditorConfigSettings.class);
  }
}
