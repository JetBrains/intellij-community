// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class LineSeparatorWidgetFactory extends StatusBarEditorBasedWidgetFactory {
  @Override
  public @NotNull String getId() {
    return StatusBar.StandardWidgets.LINE_SEPARATOR_PANEL;
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return UIBundle.message("status.bar.line.separator.widget.name");
  }

  @Override
  public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
    return new LineSeparatorPanel(project);
  }

  @Override
  public void disposeWidget(@NotNull StatusBarWidget widget) {
    Disposer.dispose(widget);
  }

  @Override
  public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
    if (!super.canBeEnabledOn(statusBar)) {
      return false;
    }
    FileEditor editor = getFileEditor(statusBar);
    if (editor != null) {
      VirtualFile file = editor.getFile();
      return file != null && LoadTextUtil.detectLineSeparator(file, true) != null;
    }
    return false;
  }
}
