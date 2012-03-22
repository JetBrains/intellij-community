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

import javax.swing.*;
import java.awt.*;

public abstract class ActionButtonLook {
  public static final ActionButtonLook IDEA_LOOK = new IdeaActionButtonLook();

  public <ButtonType extends JComponent & ActionButtonComponent> void paintBackground(Graphics g, ButtonType button) {
    paintBackground(g, button, getState(button));
  }

  public <ButtonType extends JComponent & ActionButtonComponent> void paintBorder(Graphics g, ButtonType button) {
    paintBorder(g, button, getState(button));
  }

  public abstract void paintBackground(Graphics g, JComponent component, @ActionButtonComponent.ButtonState int state);

  public abstract void paintBorder(Graphics g, JComponent component, @ActionButtonComponent.ButtonState int state);

  @SuppressWarnings("MethodMayBeStatic")
  @ActionButtonComponent.ButtonState
  protected int getState(ActionButtonComponent button) {
    // DO NOT inline this method! Because of compiler bug up-cast from ButtonType to ActionButtonComponent is important!
    return button.getPopState();
  }

  public abstract void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon);

  public abstract void paintIconAt(Graphics g, ActionButtonComponent button, Icon icon, int x, int y);
}
