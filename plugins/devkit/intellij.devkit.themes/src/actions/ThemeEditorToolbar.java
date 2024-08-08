// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.themes.ThemeJsonUtil;

import javax.swing.*;
import java.util.function.Function;

final class ThemeEditorToolbar implements EditorNotificationProvider, DumbAware {
  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    return fileEditor -> {
      if (ThemeJsonUtil.isThemeFilename(file.getName())) {
        JBColor bg = JBColor.lazy(() -> ExperimentalUI.isNewUI()
                                          ? EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()
                                          : JBColor.PanelBackground);
        EditorNotificationPanel panel = new EditorNotificationPanel(bg);
        panel.removeAll();
        panel.setBorder(null);
        DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("DevKit.ThemeEditorToolbar");
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("ThemeEditor", group, true);
        actionToolbar.setTargetComponent(panel);
        JComponent toolbarComponent = actionToolbar.getComponent();
        toolbarComponent.setBackground(bg);
        panel.add(toolbarComponent);
        return UiDataProvider.wrapComponent(panel, sink -> {
          sink.set(CommonDataKeys.VIRTUAL_FILE, fileEditor.getFile());
        });
      }
      return null;
    };
  }
}
