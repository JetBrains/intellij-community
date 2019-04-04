// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LineSeparatorPanel extends EditorBasedStatusBarPopup {
  public LineSeparatorPanel(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) {
      return WidgetState.HIDDEN;
    }
    String lineSeparator = LoadTextUtil.detectLineSeparator(file, true);
    String toolTipText;
    String panelText;
    if (lineSeparator != null) {
      toolTipText = String.format("Line separator: %s", StringUtil.escapeLineBreak(lineSeparator));
      panelText = LineSeparator.fromString(lineSeparator).toString();
    }
    else {
      toolTipText = "No line separator";
      panelText = "n/a";
    }

    return new WidgetState(toolTipText, panelText, lineSeparator != null);
  }

  @Nullable
  @Override
  protected ListPopup createPopup(DataContext context) {
    AnAction group = ActionManager.getInstance().getAction("ChangeLineSeparators");
    if (!(group instanceof ActionGroup)) {
      return null;
    }

    return JBPopupFactory.getInstance().createActionGroupPopup(
      "Line Separator",
      (ActionGroup)group,
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false
    );
  }

  @Override
  protected void registerCustomListeners() {
    // nothing
  }

  @NotNull
  @Override
  protected StatusBarWidget createInstance(Project project) {
    return new LineSeparatorPanel(project);
  }

  @NotNull
  @Override
  public String ID() {
    return "LineSeparator";
  }
}
