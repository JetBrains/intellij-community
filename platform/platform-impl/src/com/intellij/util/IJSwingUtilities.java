/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;

public class IJSwingUtilities extends JBSwingUtilities {

  /**
   * @return true if javax.swing.SwingUtilities.findFocusOwner(component) != null
   */
  public static boolean hasFocus(Component component) {
    Component focusOwner = findFocusOwner(component);
    return focusOwner != null;
  }

  private static Component findFocusOwner(Component c) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    // verify focusOwner is a descendant of c
    for (Component temp = focusOwner; temp != null; temp = (temp instanceof Window) ? null : temp.getParent())
    {
      if (temp == c) {
        return focusOwner;
      }
    }

    return null;
  }

  /**
   * @return true if window ancestor of component was most recent focused window and most recent focused component
   * in that window was descended from component
   */
  public static boolean hasFocus2(Component component) {
    WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
    Window activeWindow=null;
    if (windowManager != null) {
      activeWindow = windowManager.getMostRecentFocusedWindow();
    }
    if(activeWindow==null){
      return false;
    }
    Component focusedComponent = windowManager.getFocusedComponent(activeWindow);
    if (focusedComponent == null) {
      return false;
    }

    return SwingUtilities.isDescendingFrom(focusedComponent, component);
  }

  /**
   * This method is copied from {@code SwingUtilities}.
   * Returns index of the first occurrence of {@code mnemonic}
   * within string {@code text}. Matching algorithm is not
   * case-sensitive.
   *
   * @param text The text to search through, may be null
   * @param mnemonic The mnemonic to find the character for.
   * @return index into the string if exists, otherwise -1
   */
  public static int findDisplayedMnemonicIndex(String text, int mnemonic) {
    if (text == null || mnemonic == '\0') {
      return -1;
    }

    char uc = Character.toUpperCase((char)mnemonic);
    char lc = Character.toLowerCase((char)mnemonic);

    int uci = text.indexOf(uc);
    int lci = text.indexOf(lc);

    if (uci == -1) {
      return lci;
    } else if(lci == -1) {
      return uci;
    } else {
      return (lci < uci) ? lci : uci;
    }
  }

  public static void adjustComponentsOnMac(@Nullable JComponent component) {
    adjustComponentsOnMac(null, component);
  }


  public static void adjustComponentsOnMac(@Nullable JLabel label, @Nullable JComponent component) {
    if (component == null) return;
    if (!UIUtil.isUnderAquaLookAndFeel()) return;

    if (component instanceof JComboBox) {
      UIUtil.addInsets(component, new Insets(0,-2,0,0));
      if (label != null) {
        UIUtil.addInsets(label, new Insets(0,2,0,0));
      }
    }
    if (component instanceof JCheckBox) {
      UIUtil.addInsets(component, new Insets(0,-5,0,0));
    }
    if (component instanceof JTextField || component instanceof EditorTextField) {
      if (label != null) {
        UIUtil.addInsets(label, new Insets(0,3,0,0));
      }
    }
  }

  public static HyperlinkEvent createHyperlinkEvent(@Nullable String href, @NotNull Object source) {
    URL url = null;
    try {
      url = new URL(href);
    }
    catch (MalformedURLException ignored) {
    }
    return new HyperlinkEvent(source, HyperlinkEvent.EventType.ACTIVATED, url, href);
  }

  /**
   * A copy of javax.swing.SwingUtilities#updateComponentTreeUI that invokes children updateUI() first

   * @param c component
   * @see javax.swing.SwingUtilities#updateComponentTreeUI
   */
  public static void updateComponentTreeUI(@Nullable Component c) {
    if (c == null) return;
    for (Component component : UIUtil.uiTraverser(c).postOrderDfsTraversal()) {
      if (component instanceof JComponent) ((JComponent)component).updateUI();
    }
    c.invalidate();
    c.validate();
    c.repaint();
  }

  public static void moveMousePointerOn(Component component) {
    if (component != null && component.isShowing()) {
      UISettings settings = ApplicationManager.getApplication() == null ? null : UISettings.getInstance();
      if (settings != null && settings.getMoveMouseOnDefaultButton()) {
        Point point = component.getLocationOnScreen();
        int dx = component.getWidth() / 2;
        int dy = component.getHeight() / 2;
        try {
          new Robot().mouseMove(point.x + dx, point.y + dy);
        }
        catch (AWTException ignored) {
          // robot is not available
        }
      }
    }
  }
}