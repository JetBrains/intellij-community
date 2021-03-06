// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalActionPresentation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class TerminalActionUtil {

  private TerminalActionUtil() {}

  public static @Nullable TerminalAction createTerminalAction(@NotNull JBTerminalWidget widget,
                                                              @NonNls @NotNull String actionId,
                                                              boolean hiddenAction) {
    List<KeyStroke> keyStrokes = JBTerminalSystemSettingsProviderBase.getKeyStrokesByActionId(actionId);
    if (keyStrokes.isEmpty() && hiddenAction) return null;
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) {
      throw new AssertionError("Cannot find action " + actionId);
    }
    String name = action.getTemplateText();
    if (name != null && !hiddenAction) {
      throw new AssertionError("Action has unknown name: " + actionId);
    }
    TerminalActionPresentation presentation = new TerminalActionPresentation(StringUtil.notNullize(name, "unknown"), keyStrokes);
    return new TerminalAction(presentation, (keyEvent) -> {
      DataContext dataContext = DataManager.getInstance().getDataContext(widget.getTerminalPanel());
      ActionUtil.performActionDumbAware(action, AnActionEvent.createFromInputEvent(keyEvent, "Terminal", null, dataContext));
      return true;
    }).withHidden(hiddenAction);
  }
}
