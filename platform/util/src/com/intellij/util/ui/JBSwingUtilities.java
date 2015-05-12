/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.util.Key;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.TreeTraverser;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;

/**
 * @author gregsh
 */
public class JBSwingUtilities {

  public static final Key<Iterable<? extends Component>> NOT_IN_HIERARCHY_COMPONENTS = Key.create("NOT_IN_HIERARCHY_COMPONENTS");

  /**
   * Replaces SwingUtilities#isLeftMouseButton() for consistency with other button-related methods
   *
   * @see javax.swing.SwingUtilities#isLeftMouseButton(java.awt.event.MouseEvent)
   */
  public static boolean isLeftMouseButton(MouseEvent anEvent) {
    return (anEvent.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) > 0;
  }

  /**
   * Replaces SwingUtilities#isMiddleMouseButton() due to the fact that BUTTON2_MASK == Event.ALT_MASK
   *
   * @see javax.swing.SwingUtilities#isMiddleMouseButton(java.awt.event.MouseEvent)
   * @see java.awt.event.InputEvent#BUTTON2_MASK
   */
  public static boolean isMiddleMouseButton(MouseEvent anEvent) {
    return (anEvent.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) > 0;
  }

  /**
   * Replaces SwingUtilities#isRightMouseButton() due to the fact that BUTTON3_MASK == Event.META_MASK
   *
   * @see javax.swing.SwingUtilities#isRightMouseButton(java.awt.event.MouseEvent)
   * @see java.awt.event.InputEvent#BUTTON3_MASK
   */
  public static boolean isRightMouseButton(MouseEvent anEvent) {
    return (anEvent.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) > 0;
  }

  @NotNull
  public static TreeTraverser<Component> uiTraverser() {
    return new TreeTraverser<Component>() {
      @NotNull
      @Override
      public JBIterable<Component> children(@NotNull Component c) {
        JBIterable<Component> result;
        if (c instanceof JMenu) {
          result = JBIterable.of(((JMenu)c).getMenuComponents());
        }
        else if (c instanceof Container) {
          result = JBIterable.of(((Container)c).getComponents());
        }
        else {
          result = JBIterable.empty();
        }
        if (c instanceof JComponent) {
          JComponent jc = (JComponent)c;
          Iterable<? extends Component> orphans = UIUtil.getClientProperty(jc, NOT_IN_HIERARCHY_COMPONENTS);
          if (orphans != null) {
            result = result.append(orphans);
          }
          JPopupMenu jpm = jc.getComponentPopupMenu();
          if (jpm != null && jpm.isVisible() && jpm.getInvoker() == jc) {
            result = result.append(Collections.singletonList(jpm));
          }
        }
        return result;
      }
    };
  }
}
