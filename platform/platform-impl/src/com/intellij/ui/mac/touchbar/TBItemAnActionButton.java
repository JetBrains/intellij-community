// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import static java.awt.event.ComponentEvent.COMPONENT_FIRST;

class TBItemAnActionButton extends TBItemButton {
  private static final int ourRunConfigurationPopoverWidth = 143;

  public static final int SHOWMODE_IMAGE_ONLY = 0;
  public static final int SHOWMODE_TEXT_ONLY = 1;
  public static final int SHOWMODE_IMAGE_TEXT = 2;
  public static final int SHOWMODE_IMAGE_ONLY_IF_PRESENTED = 3;

  private @NotNull AnAction myAnAction;
  private @NotNull String myActionId;

  private int myShowMode = SHOWMODE_IMAGE_ONLY_IF_PRESENTED;
  private boolean myAutoVisibility = true;
  private boolean myHiddenWhenDisabled = false;

  private @Nullable Component myComponent;
  private @Nullable List<? extends TBItemAnActionButton> myLinkedButtons;

  TBItemAnActionButton(@Nullable ItemListener listener, @NotNull AnAction action, @Nullable TouchBarStats.AnActionStats stats) {
    super(listener, stats);
    setAnAction(action);
    setModality(null);

    if (action instanceof Toggleable) {
      myFlags |= NSTLibrary.BUTTON_FLAG_TOGGLE;
    }
  }

  @Override
  public String toString() { return String.format("%s [%s]", myActionId, getUid()); }

  TBItemAnActionButton setComponent(Component component/*for DataCtx*/) { myComponent = component; return this; }
  TBItemAnActionButton setModality(ModalityState modality) { setAction(this::_performAction, true, modality); return this; }
  TBItemAnActionButton setShowMode(int showMode) { myShowMode = showMode; return this; }

  void setLinkedButtons(@Nullable List<? extends TBItemAnActionButton> linkedButtons) { myLinkedButtons = linkedButtons; }

  boolean isAutoVisibility() { return myAutoVisibility; }
  void setAutoVisibility(boolean autoVisibility) { myAutoVisibility = autoVisibility; }

  void setHiddenWhenDisabled(boolean hiddenWhenDisabled) { myHiddenWhenDisabled = hiddenWhenDisabled; }

  @NotNull AnAction getAnAction() { return myAnAction; }
  @NotNull String getActionId() { return myActionId; }

  void setAnAction(@NotNull AnAction newAction) {
    // can be safely replaced without setAction (because _performAction will use updated reference to AnAction)
    myAnAction = newAction;
    String newActionId = ActionManager.getInstance().getId(newAction);
    myActionId = newActionId == null ? newAction.toString() : newActionId;
  }

  // returns true when visibility changed
  boolean updateVisibility(Presentation presentation) { // called from EDT
    if (!myAutoVisibility)
      return false;

    final boolean isVisible = presentation.isVisible() && (presentation.isEnabled() || !myHiddenWhenDisabled);
    boolean visibilityChanged = isVisible != myIsVisible;
    if (visibilityChanged) {
      myIsVisible = isVisible;
      // System.out.println(String.format("%s: visibility changed, now is [%s]", toString(), isVisible ? "visible" : "hidden"));
    }
    if ("RunConfiguration".equals(myActionId))
      visibilityChanged = visibilityChanged || _setLinkedVisibility(presentation.getIcon() != AllIcons.General.Add);

    return visibilityChanged;
  }

  // returns true when need to update native peer
  boolean updateView(@NotNull Presentation presentation) { // called from EDT
    if (!myIsVisible)
      return false;

    final long startNs = myActionStats != null ? System.nanoTime() : 0;
    Icon icon = null;
    boolean needGetDisabledIcon = false;
    if (myShowMode != SHOWMODE_TEXT_ONLY) {
      if (presentation.isEnabled())
        icon = presentation.getIcon();
      else {
        icon = presentation.getDisabledIcon();
        if (icon == null && presentation.getIcon() != null) {
          needGetDisabledIcon = true;
          icon = presentation.getIcon();
        }
      }
    }
    setIcon(icon, needGetDisabledIcon);
    setDisabled(!presentation.isEnabled());

    boolean isSelected = false;
    if (myAnAction instanceof Toggleable) {
      isSelected = Toggleable.isSelected(presentation);
      if (myNativePeer != ID.NIL && myActionId.startsWith("Console.Jdbc.Execute")) // permanent update of toggleable-buttons of DataGrip
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }
    setSelected(isSelected);

    if ("RunConfiguration".equals(myActionId)) {
      if (presentation.getIcon() != AllIcons.General.Add) {
        setHasArrowIcon(true);
        setLayout(ourRunConfigurationPopoverWidth, 0, 5, 8);
      } else {
        setHasArrowIcon(false);
        setLayout(0, 0, 5, 8);
      }
    }

    final boolean hideText = myShowMode == SHOWMODE_IMAGE_ONLY || (myShowMode == SHOWMODE_IMAGE_ONLY_IF_PRESENTED && icon != null);
    final String text = hideText ? null : presentation.getText();
    setText(text);

    if (myActionStats != null)
      myActionStats.updateViewNs += System.nanoTime() - startNs;

    return myUpdateOptions != 0 && myNativePeer != ID.NIL;
  }

  private boolean _setLinkedVisibility(boolean visible) {
    if (myLinkedButtons == null)
      return false;
    boolean visibilityChanged = false;
    for (TBItemAnActionButton butt: myLinkedButtons) {
      if (butt.myAutoVisibility != visible)
        visibilityChanged = true;
      butt.setAutoVisibility(visible);
      butt.myIsVisible = visible;
    }
    return visibilityChanged;
  }

  private void _performAction() {
    final ActionManagerEx actionManagerEx = ActionManagerEx.getInstanceEx();
    final Component src = getComponent();
    if (src == null) // KeyEvent can't have null source object
      return;

    final InputEvent ie = new KeyEvent(src, COMPONENT_FIRST, System.currentTimeMillis(), 0, 0, '\0');
    actionManagerEx.tryToExecute(myAnAction, ie, src, ActionPlaces.TOUCHBAR_GENERAL, true);

    if (myAnAction instanceof Toggleable) // to update 'selected'-state after action has been performed
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
  }

  Component getComponent() { return myComponent != null ? myComponent : BuildUtils.getCurrentFocusComponent(); }

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
