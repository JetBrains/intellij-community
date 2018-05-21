// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static java.awt.event.ComponentEvent.COMPONENT_FIRST;

public class TBItemAnActionButton extends TBItemButton {
  private static final boolean LOG_ICON_ERRORS = System.getProperty("touchbar.log.icon.errors", "false").equals("true");

  public static final int SHOWMODE_IMAGE_ONLY = 0;
  public static final int SHOWMODE_TEXT_ONLY = 1;
  public static final int SHOWMODE_IMAGE_TEXT = 2;
  public static final int SHOWMODE_IMAGE_ONLY_IF_PRESENTED = 3;

  private static final Logger LOG = Logger.getInstance(TBItemAnActionButton.class);

  private final AnAction myAnAction;
  private final String myActionId;

  private boolean myAutoVisibility = true;
  private final boolean myHiddenWhenDisabled;
  private final int myShowMode;

  private Component myComponent;

  TBItemAnActionButton(@NotNull String uid, @NotNull AnAction action, boolean hiddenWhenDisabled, int showMode, ModalityState modality) {
    super(uid);
    myAnAction = action;
    myActionId = ActionManager.getInstance().getId(myAnAction);
    myAction = () -> {
      if (modality != null)
        ApplicationManager.getApplication().invokeLater(() -> _performAction(), modality);
      else
        ApplicationManager.getApplication().invokeLater(() -> _performAction());
    };

    myAutoVisibility = true;
    myHiddenWhenDisabled = hiddenWhenDisabled;
    myIsVisible = false;
    myShowMode = showMode;
  }

  void setComponent(Component component/*for DataCtx*/) { myComponent = component; }

  void updateAnAction(Presentation presentation) {
    final DataContext dctx = DataManager.getInstance().getDataContext(_getComponent());
    final AnActionEvent e = new AnActionEvent(
      null,
      dctx,
      ActionPlaces.TOUCHBAR_GENERAL,
      presentation,
      ActionManagerEx.getInstanceEx(),
      0
    );
    myAnAction.update(e);
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
    if (visibilityChanged) {
      myIsVisible = isVisible;
      // LOG.info(String.format("[%s:%s] visibility changed: now is %s", myUid, myActionId, isVisible ? "visible" : "hidden"));
    }
    return visibilityChanged;
  }
  void updateView(Presentation presentation) { // called from EDT
    if (!myIsVisible)
      return;

    Icon icon = null;
    if (myShowMode != SHOWMODE_TEXT_ONLY) {
      if (presentation.isEnabled())
        icon = presentation.getIcon();
      else {
        icon = presentation.getDisabledIcon();
        if (icon == null)
          icon = IconLoader.getDisabledIcon(presentation.getIcon());
      }
      if (icon == null && LOG_ICON_ERRORS)
        LOG.error("can't get icon, action " + myActionId + ", presentation = " + _printPresentation(presentation));
    }

    boolean isSelected = false;
    if (myAnAction instanceof ToggleAction) {
      final Object selectedProp = presentation.getClientProperty(Toggleable.SELECTED_PROPERTY);
      isSelected = selectedProp != null && selectedProp == Boolean.TRUE;
    }

    final boolean hideText = myShowMode == SHOWMODE_IMAGE_ONLY || (myShowMode == SHOWMODE_IMAGE_ONLY_IF_PRESENTED && icon != null);
    final String text = hideText ? null : presentation.getText();

    update(icon, text, isSelected, !presentation.isEnabled());
  }

  private void _performAction() {
    final ActionManagerEx actionManagerEx = ActionManagerEx.getInstanceEx();
    final Component src = _getComponent();
    final InputEvent ie = new KeyEvent(src, COMPONENT_FIRST, System.currentTimeMillis(), 0, 0, '\0');
    actionManagerEx.tryToExecute(myAnAction, ie, src, ActionPlaces.TOUCHBAR_GENERAL, true);
  }

  private Component _getComponent() { return myComponent != null ? myComponent : _getCurrentFocusComponent(); }

  private static Component _getCurrentFocusComponent() {
    final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner = focusManager.getFocusOwner();
    if (focusOwner == null) {
      // LOG.info(String.format("WARNING: [%s:%s] _getCurrentFocusContext: null focus-owner, use focused window", myUid, myActionId));
      return focusManager.getFocusedWindow();
    }
    return focusOwner;
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
