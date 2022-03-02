// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public final class ThemeEditorToolbar extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("ThemeEditorToolbar");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (ThemeJsonUtil.isThemeFilename(file.getName())) {
      JBColor bg = JBColor.lazy(() -> ExperimentalUI.isNewEditorTabs()
                                        ? EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()
                                        : JBColor.PanelBackground);
      EditorNotificationPanel panel = new EditorNotificationPanel(bg);
      panel.removeAll();
      DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("DevKit.ThemeEditorToolbar");
      JComponent toolbar = ActionManager.getInstance().createActionToolbar("ThemeEditor", group, true).getComponent();
      toolbar.setBackground(bg);
      panel.add(toolbar);
      DataManager.registerDataProvider(panel, dataId -> CommonDataKeys.VIRTUAL_FILE.is(dataId) ? fileEditor.getFile() : null);
      return panel;
    }
    return null;
  }
}
