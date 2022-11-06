// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

final class NoSwingUnderWriteAction {
  private static final Logger LOG = Logger.getInstance(NoSwingUnderWriteAction.class);

  static void watchForEvents(@NotNull Application app) {
    AtomicBoolean reported = new AtomicBoolean();
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (app.isWriteAccessAllowed() && reported.compareAndSet(false, true)) {
        LOG.error("AWT events are not allowed inside write action: " + e);
      }
      return true;
    }, app);

    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void afterWriteActionFinished(@NotNull Object action) {
        reported.set(false);
      }
    }, app);
  }
}
