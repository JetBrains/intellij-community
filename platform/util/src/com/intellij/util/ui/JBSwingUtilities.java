// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
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

  /**
   * Replaces SwingUtilities#isLeftMouseButton() for consistency with other button-related methods
   *
   * @see SwingUtilities#isLeftMouseButton(MouseEvent)
   */
  public static boolean isLeftMouseButton(MouseEvent anEvent) {
    return SwingUtilities.isLeftMouseButton(anEvent);
  }

  /**
   * Replaces SwingUtilities#isMiddleMouseButton() due to the fact that BUTTON2_MASK == Event.ALT_MASK
   *
   * @see SwingUtilities#isMiddleMouseButton(MouseEvent)
   * @see InputEvent#BUTTON2_MASK
   */
  public static boolean isMiddleMouseButton(MouseEvent anEvent) {
    return SwingUtilities.isMiddleMouseButton(anEvent);
  }

  /**
   * Replaces SwingUtilities#isRightMouseButton() due to the fact that BUTTON3_MASK == Event.META_MASK
   *
   * @see SwingUtilities#isRightMouseButton(MouseEvent)
   * @see InputEvent#BUTTON3_MASK
   */
  public static boolean isRightMouseButton(MouseEvent anEvent) {
    return SwingUtilities.isRightMouseButton(anEvent);
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
