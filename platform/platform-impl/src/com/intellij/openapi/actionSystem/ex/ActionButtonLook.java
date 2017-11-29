/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook;
import com.intellij.openapi.actionSystem.impl.Win10ActionButtonLook;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public abstract class ActionButtonLook {
  public static final ActionButtonLook SYSTEM_LOOK = new ActionButtonLook() {
    private ActionButtonLook delegate;

    { updateUI(); }

    @Override public void updateUI() {
      delegate = UIUtil.isUnderWin10LookAndFeel() ? new Win10ActionButtonLook() : new IdeaActionButtonLook();
    }

    @Override public void paintBackground(Graphics g, JComponent component, int state) {
      delegate.paintBackground(g, component, state);
    }

    @Override public void paintBorder(Graphics g, JComponent component, int state) {
      delegate.paintBorder(g, component, state);
    }

    @Override public Insets getInsets() {
      return delegate.getInsets();
    }
  };

  public static final ActionButtonLook INPLACE_LOOK = new ActionButtonLook() {
    @Override public void paintBackground(Graphics g, JComponent component, int state) {}
    @Override public void paintBorder(Graphics g, JComponent component, int state) {}
  };

  public <ButtonType extends JComponent & ActionButtonComponent> void paintBackground(Graphics g, ButtonType button) {
    paintBackground(g, button, getState(button));
  }

  public <ButtonType extends JComponent & ActionButtonComponent> void paintBorder(Graphics g, ButtonType button) {
    paintBorder(g, button, getState(button));
  }

  public abstract void paintBackground(Graphics g, JComponent component, @ActionButtonComponent.ButtonState int state);

  public abstract void paintBorder(Graphics g, JComponent component, @ActionButtonComponent.ButtonState int state);

  public void updateUI() {}

  @SuppressWarnings("MethodMayBeStatic")
  @ActionButtonComponent.ButtonState
  protected int getState(ActionButtonComponent button) {
    // DO NOT inline this method! Because of compiler bug up-cast from ButtonType to ActionButtonComponent is important!
    return button.getPopState();
  }

  public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon) {
    int width = icon.getIconWidth();
    int height = icon.getIconHeight();
    int x = (actionButton.getWidth() - width) / 2;
    int y = (actionButton.getHeight() - height) / 2;
    paintIconAt(g, icon, x, y);
  }

  public void paintIconAt(Graphics g, Icon icon, int x, int y) {
    icon.paintIcon(null, g, x, y);
  }

  public Insets getInsets() {
    return JBUI.emptyInsets();
  }
}
