// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author gregsh
 *
 * Note: seems to be unnecessary in Java 8 and up.
 */
public final class JBSwingUtilities {
  /**
   * @deprecated Use {@link SwingUtilities#isLeftMouseButton}
   */
  @Deprecated
  public static boolean isLeftMouseButton(MouseEvent anEvent) {
    return SwingUtilities.isLeftMouseButton(anEvent);
  }

  /**
   * @deprecated Use {@link SwingUtilities#isRightMouseButton}
   */
  @Deprecated
  public static boolean isRightMouseButton(MouseEvent anEvent) {
    return SwingUtilities.isRightMouseButton(anEvent);
  }


  private static final List<PairFunction<? super JComponent, ? super Graphics2D, ? extends Graphics2D>> ourGlobalTransform =
    ContainerUtil.createEmptyCOWList();

  public static Disposable addGlobalCGTransform(final PairFunction<? super JComponent, ? super Graphics2D, ? extends Graphics2D> fun) {
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
    for (PairFunction<? super JComponent, ? super Graphics2D, ? extends Graphics2D> transform : ourGlobalTransform) {
      gg = ObjectUtils.notNull(transform.fun(c, gg));
    }
    return gg;
  }
}
