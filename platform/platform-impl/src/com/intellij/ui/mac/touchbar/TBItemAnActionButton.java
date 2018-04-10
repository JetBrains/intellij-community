// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static java.awt.event.ComponentEvent.COMPONENT_FIRST;

public class TBItemAnActionButton extends TBItemButton {
  private static final Logger LOG = Logger.getInstance(TBItemAnActionButton.class);

  private final AnAction myAnAction;
  private final String myActionId;

  private boolean myAutoVisibility = true;
  private final boolean myHiddenWhenDisabled;
  private final boolean myOnlyImage = true;

  TBItemAnActionButton(@NotNull String uid, @NotNull AnAction action, boolean hiddenWhenDisabled) {
    super(uid);
    myAnAction = action;
    myActionId = ActionManager.getInstance().getId(myAnAction);
    myAction = () -> ApplicationManager.getApplication().invokeLater(() -> _performAction());

    myAutoVisibility = true;
    myHiddenWhenDisabled = hiddenWhenDisabled;
    myIsVisible = false;
  }

  boolean isAutoVisibility() { return myAutoVisibility; }
  public void setAutoVisibility(boolean autoVisibility) { myAutoVisibility = autoVisibility; }

  AnAction getAnAction() { return myAnAction; }

  // returns true when visibility changed
  boolean updateVisibility(Presentation presentation) { // called from EDT
    if (!myAutoVisibility)
      return false;

    final boolean isVisible = presentation.isVisible() && (presentation.isEnabled() || !myHiddenWhenDisabled);
    final boolean visibilityChanged = isVisible != myIsVisible;
    if (visibilityChanged)
      myIsVisible = isVisible;
    return visibilityChanged;
  }
  void updateView(Presentation presentation) { // called from EDT
    if (!myIsVisible)
      return;

    Icon icon;
    if (presentation.isEnabled())
      icon = presentation.getIcon();
    else {
      icon = presentation.getDisabledIcon();
      if (icon == null)
        icon = IconLoader.getDisabledIcon(presentation.getIcon());
    }
    if (icon == null) {
      LOG.error("can't get icon, action " + myActionId + ", presentation = " + _printPresentation(presentation));
      icon = EmptyIcon.ICON_18;
    }

    final String text = myOnlyImage ? null : presentation.getText();

    // update native peer only when some of resources has been changed
    if (
      icon != myIcon ||
      (text == null ? myText != null : text.equals(myText))
    ) {
      System.out.println("\tupdateView ["+myUid+"]");
      update(icon, text);
    }
  }

  private void _performAction() {
    // TODO: check action state (isEnabled, canBePerformed)
    final ActionManagerEx actionManagerEx = ActionManagerEx.getInstanceEx();
    final KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    final Component focusOwner = focusManager.getFocusedWindow();

    final InputEvent ie = new KeyEvent(focusOwner, COMPONENT_FIRST, System.currentTimeMillis(), 0, 0, '\0');
    actionManagerEx.tryToExecute(myAnAction, ie, focusOwner, ActionPlaces.UNKNOWN, false);
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
