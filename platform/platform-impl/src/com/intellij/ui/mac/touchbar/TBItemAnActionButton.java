// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static java.awt.event.ComponentEvent.COMPONENT_FIRST;

class TBItemAnActionButton extends TBItemButton {
  static final int SHOWMODE_IMAGE_ONLY = 0;
  static final int SHOWMODE_TEXT_ONLY = 1;
  static final int SHOWMODE_IMAGE_TEXT = 2;

  private @NotNull AnAction myAnAction;
  private @NotNull String myActionId;

  private @Nullable Component myComponent;

  TBItemAnActionButton(@Nullable ItemListener listener, @NotNull AnAction action, @Nullable TouchBarStats.AnActionStats stats) {
    super(listener, stats);
    setAnAction(action);
    setAction(this::_performAction, true);

    if (action instanceof Toggleable) {
      myFlags |= NSTLibrary.BUTTON_FLAG_TOGGLE;
    }
  }

  @Override
  public String toString() { return String.format("%s [%s]", myActionId, getUid()); }

  void setComponent(@Nullable Component component/*for DataCtx*/) { myComponent = component; }

  @NotNull AnAction getAnAction() { return myAnAction; }
  @NotNull String getActionId() { return myActionId; }

  void setAnAction(@NotNull AnAction newAction) {
    myAnAction = newAction;
    String newActionId = ActionManager.getInstance().getId(newAction);
    myActionId = newActionId == null ? newAction.toString() : newActionId;
  }

  private void _performAction() {
    final ActionManagerEx actionManagerEx = ActionManagerEx.getInstanceEx();
    final Component src = myComponent != null ? myComponent : Helpers.getCurrentFocusComponent();
    if (src == null) // KeyEvent can't have null source object
      return;

    final InputEvent ie = new KeyEvent(src, COMPONENT_FIRST, System.currentTimeMillis(), 0, 0, '\0');
    actionManagerEx.tryToExecute(myAnAction, ie, src, ActionPlaces.TOUCHBAR_GENERAL, true);

    if (myAnAction instanceof Toggleable) // to update 'selected'-state after action has been performed
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
  }

  private static String _printPresentation(Presentation presentation) {
    StringBuilder sb = new StringBuilder();

    if (presentation.getText() != null && !presentation.getText().isEmpty())
      sb.append(String.format("text='%s'", presentation.getText()));

    {
      final Icon icon = presentation.getIcon();
      if (icon != null) {
        if (sb.length() != 0)
          sb.append(", ");
        sb.append(String.format("icon: %dx%d", icon.getIconWidth(), icon.getIconHeight()));
      }
    }

    {
      final Icon disabledIcon = presentation.getDisabledIcon();
      if (disabledIcon != null) {
        if (sb.length() != 0)
          sb.append(", ");
        sb.append(String.format("dis-icon: %dx%d", disabledIcon.getIconWidth(), disabledIcon.getIconHeight()));
      }
    }

    if (sb.length() != 0)
      sb.append(", ");
    sb.append(presentation.isVisible() ? "visible" : "hidden");

    sb.append(presentation.isEnabled() ? ", enabled" : ", disabled");

    return sb.toString();
  }
}
