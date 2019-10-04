// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public final class LineSeparatorPanel extends EditorBasedStatusBarPopup {
  public LineSeparatorPanel(@NotNull Project project) {
    super(project, true);
  }

  @NotNull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) {
      return WidgetState.HIDDEN;
    }
    String lineSeparator = LoadTextUtil.detectLineSeparator(file, true);
    if (lineSeparator == null) {
      return WidgetState.HIDDEN;
    }
    String toolTipText = String.format("Line Separator: %s", StringUtil.escapeLineBreak(lineSeparator));
    String panelText = LineSeparator.fromString(lineSeparator).toString();
    return new WidgetState(toolTipText, panelText, true);
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

  @NotNull
  @Override
  protected StatusBarWidget createInstance(@NotNull Project project) {
    return new LineSeparatorPanel(project);
  }

  @NotNull
  @Override
  public String ID() {
    return "LineSeparator";
  }
}
