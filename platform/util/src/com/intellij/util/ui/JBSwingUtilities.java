/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author gregsh
 *
 * Note: seems to be unnecessary in Java 8 and up.
 */
public class JBSwingUtilities {

  private static final boolean LEGACY_JDK = !SystemInfo.isJavaVersionAtLeast("1.8");

  /**
   * Replaces SwingUtilities#isLeftMouseButton() for consistency with other button-related methods
   *
   * @see SwingUtilities#isLeftMouseButton(MouseEvent)
   */
  public static boolean isLeftMouseButton(MouseEvent anEvent) {
    return LEGACY_JDK ? (anEvent.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) > 0 : SwingUtilities.isLeftMouseButton(anEvent);
  }

  /**
   * Replaces SwingUtilities#isMiddleMouseButton() due to the fact that BUTTON2_MASK == Event.ALT_MASK
   *
   * @see SwingUtilities#isMiddleMouseButton(MouseEvent)
   * @see InputEvent#BUTTON2_MASK
   */
  public static boolean isMiddleMouseButton(MouseEvent anEvent) {
    return LEGACY_JDK ? (anEvent.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) > 0 : SwingUtilities.isMiddleMouseButton(anEvent);
  }

  /**
   * Replaces SwingUtilities#isRightMouseButton() due to the fact that BUTTON3_MASK == Event.META_MASK
   *
   * @see SwingUtilities#isRightMouseButton(MouseEvent)
   * @see InputEvent#BUTTON3_MASK
   */
  public static boolean isRightMouseButton(MouseEvent anEvent) {
    return LEGACY_JDK ? (anEvent.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) > 0 : SwingUtilities.isRightMouseButton(anEvent);
  }


  private static final List<PairFunction<JComponent, Graphics2D, Graphics2D>> ourGlobalTransform =
    ContainerUtil.createEmptyCOWList();

  public static Disposable addGlobalCGTransform(final PairFunction<JComponent, Graphics2D, Graphics2D> fun) {
    ourGlobalTransform.add(fun);
    return new Disposable() {
      @Override
      public void dispose() {
        ourGlobalTransform.remove(fun);
      }
    };
  }

  @NotNull
  public static Graphics2D runGlobalCGTransform(@NotNull JComponent c, @NotNull Graphics g) {
    Graphics2D gg = (Graphics2D)g;
    for (PairFunction<JComponent, Graphics2D, Graphics2D> transform : ourGlobalTransform) {
      gg = ObjectUtils.notNull(transform.fun(c, gg));
    }
    return gg;
  }
}
