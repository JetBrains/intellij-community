// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.TempUIThemeBasedLookAndFeelInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class ApplyThemeAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    VirtualFile file = fromMouseEvent(e);

    if (file == null) {
      file = e.getData(CommonDataKeys.VIRTUAL_FILE);
      if (file == null || !UITheme.isThemeFile(file)) {
        for (FileEditor fileEditor : FileEditorManager.getInstance(project).getSelectedEditors()) {
          if (UITheme.isThemeFile(fileEditor.getFile())) {
            file = fileEditor.getFile();
            break;
          }
        }
      }
    }

    if (file != null && UITheme.isThemeFile(file)) {
      applyTempTheme(file);
    }
  }

  private VirtualFile fromMouseEvent(AnActionEvent e) {
    if (e.getInputEvent() instanceof MouseEvent) {
      Component component = e.getInputEvent().getComponent();
      EditorNotificationPanel panel = UIUtil.getParentOfType(EditorNotificationPanel.class, component);
      if (panel != null) {
      }
    }
return null;
  }

  private static void applyTempTheme(@NotNull VirtualFile json) {
    try {
      FileDocumentManager.getInstance().saveAllDocuments();
      UITheme theme = UITheme.loadFromJson(json.getInputStream(), "Temp theme", null);
      LafManager.getInstance().setCurrentLookAndFeel(new TempUIThemeBasedLookAndFeelInfo(theme));
      LafManager.getInstance().updateUI();
    }
    catch (IOException ignore) {}
  }
}
