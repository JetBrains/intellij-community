// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;

/**
 * @author lex
 */
public interface ProgressIndicatorListener {
  void cancelled();

  void stopped();

  default void installToProgressIfPossible(ProgressIndicator progress) {
    if (progress instanceof ProgressIndicatorEx) {
      installToProgress((ProgressIndicatorEx)progress);
    }
  }

  default void installToProgress(ProgressIndicatorEx progress) {
    progress.addStateDelegate(new AbstractProgressIndicatorExBase(){
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
    });
  }
}
