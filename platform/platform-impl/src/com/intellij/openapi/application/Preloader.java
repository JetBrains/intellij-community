// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.storage.HeavyProcessLatch;

import java.util.concurrent.Executor;

/**
 * @author peter
 */
public class Preloader implements Disposable, BaseComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.Preloader");
  private final Executor myExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Preloader Pool");
  private final ProgressIndicator myIndicator = new ProgressIndicatorBase();
  private final ProgressIndicator myWrappingIndicator = new AbstractProgressIndicatorBase() {
    @Override
    public void checkCanceled() {
      checkHeavyProcessRunning();
      myIndicator.checkCanceled();
    }

    @Override
    public boolean isCanceled() {
      return myIndicator.isCanceled();
    }
  };

  private static void checkHeavyProcessRunning() {
    if (HeavyProcessLatch.INSTANCE.isRunning()) {
      TimeoutUtil.sleep(1);
    }
  }

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    ProgressManager progressManager = ProgressManager.getInstance();
    for (final PreloadingActivity activity : PreloadingActivity.EP_NAME.getExtensions()) {
      myExecutor.execute(() -> {
        if (myIndicator.isCanceled()) return;

        checkHeavyProcessRunning();
        if (myIndicator.isCanceled()) return;

        progressManager.runProcess(() -> {
          long startTime = System.nanoTime();
          try {
            activity.preload(myWrappingIndicator);
          }
          catch (ProcessCanceledException ignore) {
          }
          long ms = (System.nanoTime() - startTime) / 1000000;
          LOG.info(activity.getClass().getName() + " took " + ms + " ms");
        }, myIndicator);
      });
    }
  }

  @Override
  public void dispose() {
    myIndicator.cancel();
  }
}
