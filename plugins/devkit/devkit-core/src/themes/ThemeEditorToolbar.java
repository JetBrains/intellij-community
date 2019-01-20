// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class ThemeEditorToolbar extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("ThemeEditorToolbar");
  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (StringUtil.endsWithIgnoreCase(file.getName(), ".theme.json")) {
      EditorNotificationPanel panel = new EditorNotificationPanel(JBColor.PanelBackground);
      panel.removeAll();
      DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("DevKit.ThemeEditorToolbar");
      panel.add(ActionManager.getInstance().createActionToolbar("ThemeEditor", group, true).getComponent());
      DataManager.registerDataProvider(panel, new DataProvider() {
        @Nullable
        @Override
        public Object getData(@NotNull String dataId) {
          if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
            return fileEditor.getFile();
          }
          return null;
        }
      });
      return panel;
    }
    return null;
  }

}
