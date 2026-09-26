// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ui.HwFacadeProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.Graphics;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class JcefHwFacadeProvider implements HwFacadeProvider {
  @Override
  public boolean isAvailable() {
    return JBCefApp.isSupported();
  }

  @Override
  public @NotNull com.intellij.ui.HwFacadeHelper create(@NotNull JComponent target) {
    HwFacadeHelper delegate = HwFacadeHelper.create(target);
    return new com.intellij.ui.HwFacadeHelper() {
      @Override
      public void addNotify() {
        delegate.addNotify();
      }

      @Override
      public void removeNotify() {
        delegate.removeNotify();
      }

      @Override
      public void show() {
        delegate.show();
      }

      @Override
      public void hide() {
        delegate.hide();
      }

      @Override
      public void paint(@NotNull Graphics g, @NotNull Consumer<? super Graphics> targetPaint) {
        delegate.paint(g, targetPaint);
      }
    };
  }
}
