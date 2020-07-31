// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getActionLinkSelectionColor;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getMainBackground;

public class WelcomeScreenFocusManager {

  static void installFocusable(@NotNull final Container parentContainer,
                               final JComponent comp,
                               final AnAction action,
                               final int nextKeyCode,
                               final int prevKeyCode,
                               @Nullable final Component focusedOnLeft) {
    comp.setFocusable(true);
    comp.setFocusTraversalKeysEnabled(true);
    comp.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
          InputEvent event = e;
          if (e.getComponent() instanceof JComponent) {
            ActionLink link = UIUtil.findComponentOfType((JComponent)e.getComponent(), ActionLink.class);
            if (link != null) {
              event = new MouseEvent(link, MouseEvent.MOUSE_CLICKED, e.getWhen(), e.getModifiers(), 0, 0, 1, false, MouseEvent.BUTTON1);
            }
          }
          action.actionPerformed(
            AnActionEvent.createFromAnAction(action, event, ActionPlaces.WELCOME_SCREEN, DataManager.getInstance().getDataContext()));
        }
        else if (e.getKeyCode() == prevKeyCode) {
          focusPrev(parentContainer, comp);
        }
        else if (e.getKeyCode() == nextKeyCode) {
          focusNext(parentContainer, comp);
        }
        else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
          if (focusedOnLeft != null) {
            IdeFocusManager.getGlobalInstance()
              .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(focusedOnLeft, true));
          }
          else {
            focusPrev(parentContainer, comp);
          }
        }
        else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          focusNext(parentContainer, comp);
        }
      }
    });
    comp.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        comp.setOpaque(true);
        comp.setBackground(getActionLinkSelectionColor());
      }

      @Override
      public void focusLost(FocusEvent e) {
        comp.setOpaque(false);
        comp.setBackground(getMainBackground());
      }
    });
  }

  private static void focusPrev(@NotNull Container container, JComponent comp) {
    FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
    if (policy != null) {
      Component prev = policy.getComponentBefore(container, comp);
      if (prev != null) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(prev, true));
      }
    }
  }

  private static void focusNext(@NotNull Container container, JComponent comp) {
    FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
    if (policy != null) {
      Component next = policy.getComponentAfter(container, comp);
      if (next != null) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(next, true));
      }
    }
  }
}
