// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author peter
 */
class NoSwingUnderWriteAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.NoSwingUnderWriteAction");

  static void watchForEvents(Application application) {
    AtomicBoolean reported = new AtomicBoolean();
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (application.isWriteAccessAllowed() && reported.compareAndSet(false, true)) {
        LOG.error("AWT events are not allowed inside write action: " + e);
      }
      return true;
    }, application);

    application.addApplicationListener(new ApplicationListener() {
      @Override
      public void afterWriteActionFinished(@NotNull Object action) {
        reported.set(false);
      }
    });
  }
}
