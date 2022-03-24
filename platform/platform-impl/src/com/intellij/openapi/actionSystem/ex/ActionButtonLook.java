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
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class ActionButtonLook {
  public static final ActionButtonLook SYSTEM_LOOK = new ActionButtonLook() {
    private ActionButtonLook delegate;

    {
      updateUI();
    }

    @Override
    public void updateUI() {
      delegate = UIUtil.isUnderWin10LookAndFeel() ? new Win10ActionButtonLook() : new IdeaActionButtonLook();
    }

    @Override
    public void paintBackground(Graphics g, JComponent component, int state) {
      delegate.paintBackground(g, component, state);
    }

    @Override
    public void paintBackground(Graphics g, JComponent component, Color color) {
      delegate.paintBackground(g, component, color);
    }

    @Override
    public void paintBorder(Graphics g, JComponent component, int state) {
      delegate.paintBorder(g, component, state);
    }

    @Override
    public void paintBorder(Graphics g, JComponent component, Color color) {
      delegate.paintBorder(g, component, color);
    }

    @Override
    public void paintLookBackground(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
      delegate.paintLookBackground(g, rect, color);
    }

    @Override
    public void paintLookBorder(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
      delegate.paintLookBorder(g, rect, color);
    }
  };

  public static final ActionButtonLook INPLACE_LOOK = new ActionButtonLook() {
    @Override
    public void paintBackground(Graphics g, JComponent component, int state) {}

    @Override
    public void paintBorder(Graphics g, JComponent component, int state) {}

    @Override
    public void paintBackground(Graphics g, JComponent component, Color color) {}

    @Override
    public void paintBorder(Graphics g, JComponent component, Color color) {}
  };

  public <ButtonType extends JComponent & ActionButtonComponent> void paintBackground(Graphics g, ButtonType button) {
    paintBackground(g, button, getState(button));
  }

  public <ButtonType extends JComponent & ActionButtonComponent> void paintBorder(Graphics g, ButtonType button) {
    paintBorder(g, button, getState(button));
  }

  public void paintBackground(Graphics g, JComponent component, @ActionButtonComponent.ButtonState int state) {
    if (state == ActionButtonComponent.NORMAL && !component.isBackgroundSet()) return;
    Rectangle rect = new Rectangle(component.getSize());
    JBInsets.removeFrom(rect, component.getInsets());
    Color color = getStateBackground(component, state);
    paintLookBackground(g, rect, color);
  }

  protected Color getStateBackground(JComponent component, int state) {
    return state == ActionButtonComponent.NORMAL ? component.getBackground() :
                  state == ActionButtonComponent.PUSHED ? JBUI.CurrentTheme.ActionButton.pressedBackground() :
                  JBUI.CurrentTheme.ActionButton.hoverBackground();
  }

  public void paintBackground(Graphics g, JComponent component, Color color) {
    Rectangle rect = new Rectangle(component.getSize());
    JBInsets.removeFrom(rect, component.getInsets());
    paintLookBackground(g, rect, color);
  }

  public void paintBorder(Graphics g, JComponent component, @ActionButtonComponent.ButtonState int state) {
    if (state == ActionButtonComponent.NORMAL && !component.isBackgroundSet()) return;
    Rectangle rect = new Rectangle(component.getSize());
    JBInsets.removeFrom(rect, component.getInsets());
    Color color = state == ActionButtonComponent.PUSHED ? JBUI.CurrentTheme.ActionButton.pressedBorder() :
                  JBUI.CurrentTheme.ActionButton.hoverBorder();
    paintLookBorder(g, rect, color);
  }

  public void paintBorder(Graphics g, JComponent component, Color color) {
    Rectangle rect = new Rectangle(component.getSize());
    JBInsets.removeFrom(rect, component.getInsets());
    paintLookBorder(g, rect, color);
  }

  public void paintLookBackground(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {}

  public void paintLookBorder(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {}

  public void updateUI() {}

  @ActionButtonComponent.ButtonState
  protected int getState(ActionButtonComponent button) {
    // DO NOT inline this method! Because of compiler bug up-cast from ButtonType to ActionButtonComponent is important!
    return button.getPopState();
  }

  public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon) {
    Rectangle rect = new Rectangle(actionButton.getWidth(), actionButton.getHeight());
    Insets i = actionButton.getInsets();
    JBInsets.removeFrom(rect, i);

    int x = i.left + (rect.width - icon.getIconWidth()) / 2;
    int y = i.top + (rect.height - icon.getIconHeight()) / 2;
    paintIcon(g, actionButton, icon, x, y);
  }

  public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon, int x, int y) {
    icon.paintIcon(actionButton instanceof Component ? (Component)actionButton : null, g, x, y);
  }
}