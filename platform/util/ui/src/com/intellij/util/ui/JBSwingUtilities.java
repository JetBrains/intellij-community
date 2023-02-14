// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * @author gregsh
 *
 * Note: seems to be unnecessary in Java 8 and up.
 */
public final class JBSwingUtilities {
  /**
   * @deprecated Use {@link SwingUtilities#isLeftMouseButton}
   */
  @Deprecated(forRemoval = true)
  public static boolean isLeftMouseButton(MouseEvent anEvent) {
    return SwingUtilities.isLeftMouseButton(anEvent);
  }

  /**
   * @deprecated Use {@link SwingUtilities#isRightMouseButton}
   */
  @Deprecated(forRemoval = true)
  public static boolean isRightMouseButton(MouseEvent anEvent) {
    return SwingUtilities.isRightMouseButton(anEvent);
  }

  private static final List<BiFunction<? super JComponent, ? super Graphics2D, ? extends Graphics2D>> ourGlobalTransform =
    new CopyOnWriteArrayList<>(Collections.emptyList());

  public static Disposable addGlobalCGTransform(@NotNull BiFunction<? super JComponent, ? super Graphics2D, ? extends Graphics2D> fun) {
    ourGlobalTransform.add(fun);
    return new Disposable() {
      @Override
      public void dispose() {
        ourGlobalTransform.remove(fun);
      }
    };
  }

  public static @NotNull Graphics2D runGlobalCGTransform(@NotNull JComponent c, @NotNull Graphics g) {
    Graphics2D gg = (Graphics2D)g;
    for (BiFunction<? super JComponent, ? super Graphics2D, ? extends Graphics2D> transform : ourGlobalTransform) {
      gg = Objects.requireNonNull(transform.apply(c, gg));
    }
    return gg;
  }
}
