// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * @author lex
 */
public interface ProgressIndicatorListener {
  default void cancelled() { }

  default void stopped() { }

  default void onFractionChanged(double fraction) { }

  default void installToProgressIfPossible(ProgressIndicator progress) {
    if (progress instanceof ProgressIndicatorEx) {
      installToProgress((ProgressIndicatorEx)progress);
    }
  }

  default void installToProgress(ProgressIndicatorEx progress) {
    progress.addStateDelegate(new AbstractProgressIndicatorExBase() {
      @Override
      public void cancel() {
        super.cancel();
        cancelled();
      }

      @Override
      public void stop() {
        super.stop();
        stopped();
      }

      @Override
      public void setFraction(double fraction) {
        super.setFraction(fraction);
        onFractionChanged(fraction);
      }
    });
  }

  static void whenProgressFractionChanged(@NotNull ProgressIndicator progress, @NotNull Consumer<? super Double> consumer) {
    ProgressIndicatorListener listener = new ProgressIndicatorListener() {
      @Override
      public void onFractionChanged(double fraction) {
        consumer.accept(fraction);
      }
    };
    listener.installToProgressIfPossible(progress);
  }
}
