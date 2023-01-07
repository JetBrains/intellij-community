// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

import static java.lang.String.format;

public class FieldInplaceActionButtonLook extends IdeaActionButtonLook {

  private static final JBColor BUTTON_SELECTED_BACKGROUND =
    JBColor.namedColor("SearchOption.selectedBackground", 0xDAE4ED, 0x5C6164);
  private static final JBColor BUTTON_SELECTED_PRESSED_BACKGROUND =
    JBColor.namedColor("SearchOption.selectedPressedBackground", JBUI.CurrentTheme.ActionButton.pressedBackground());
  private static final JBColor BUTTON_SELECTED_HOVERED_BACKGROUND =
    JBColor.namedColor("SearchOption.selectedHoveredBackground", JBUI.CurrentTheme.ActionButton.pressedBackground());

  @Override
  public void paintBorder(Graphics g, JComponent component, @ActionButtonComponent.ButtonState int state) {
    if (component.isFocusOwner() && component.isEnabled()) {
      Rectangle rect = new Rectangle(component.getSize());
      JBInsets.removeFrom(rect, component.getInsets());
      SYSTEM_LOOK.paintLookBorder(g, rect, JBUI.CurrentTheme.ActionButton.focusedBorder());
    }
    else {
      super.paintBorder(g, component, ActionButtonComponent.NORMAL);
    }
  }

  @Override
  public void paintBackground(Graphics g, JComponent component, int state) {
    if (!(component instanceof ActionButton)) failBecauseOfWrongComponent();

    ActionButton actionButton = (ActionButton)component;
    boolean isSelected = actionButton.isSelected();
    boolean isRollover = actionButton.isRollover();

    if (isRollover) {
      super.paintBackground(g, component, state);
    }
    else if (state == ActionButtonComponent.SELECTED && component.isEnabled()) {
      Rectangle rect = new Rectangle(component.getSize());
      JBInsets.removeFrom(rect, component.getInsets());
      if (!ExperimentalUI.isNewUI() || isSelected) {
        paintLookBackground(g, rect, BUTTON_SELECTED_BACKGROUND);
      }
    }
  }

  @Override
  protected Color getStateBackground(JComponent component, int state) {
    if (!(component instanceof ActionButton)) failBecauseOfWrongComponent();

    if (ExperimentalUI.isNewUI()) {
      if (state == ActionButtonComponent.SELECTED) {
        ActionButton actionButton = (ActionButton)component;
        boolean isMouseDown = actionButton.isMouseDown();
        return isMouseDown ? BUTTON_SELECTED_PRESSED_BACKGROUND : BUTTON_SELECTED_HOVERED_BACKGROUND;
      }
    }

    return super.getStateBackground(component, state);
  }


  private static void failBecauseOfWrongComponent() {
    throw new IllegalStateException(format("The look&feel %s can works only with %s, don't use it with other components",
                                           FieldInplaceActionButtonLook.class.getSimpleName(), ActionButton.class.getSimpleName()));
  }

}
