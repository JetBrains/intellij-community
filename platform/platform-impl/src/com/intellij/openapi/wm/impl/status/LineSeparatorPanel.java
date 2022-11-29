// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LineSeparatorPanel extends EditorBasedStatusBarPopup {
  protected LineSeparatorPanel(@NotNull Project project) {
    super(project, true);
  }

  @Override
  protected @NotNull WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) {
      return WidgetState.HIDDEN;
    }
    String lineSeparator = FileDocumentManager.getInstance().getLineSeparator(file, getProject());
    String toolTipText = IdeBundle.message("tooltip.line.separator", StringUtil.escapeLineBreak(lineSeparator));
    String panelText = LineSeparator.fromString(lineSeparator).toString();
    return new WidgetState(toolTipText, panelText, true);
  }

  @Override
  protected @Nullable ListPopup createPopup(@NotNull DataContext context) {
    AnAction group = ActionManager.getInstance().getAction("ChangeLineSeparators");
    if (!(group instanceof ActionGroup)) {
      return null;
    }

    return JBPopupFactory.getInstance().createActionGroupPopup(
      UIBundle.message("status.bar.line.separator.widget.name"),
      (ActionGroup)group,
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false
    );
  }

  @Override
  protected @NotNull StatusBarWidget createInstance(@NotNull Project project) {
    return new LineSeparatorPanel(project);
  }

  @Override
  public @NotNull String ID() {
    return StatusBar.StandardWidgets.LINE_SEPARATOR_PANEL;
  }
}
