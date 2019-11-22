// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.storage.HeavyProcessLatch;

import java.util.concurrent.Executor;

/**
 * @author peter
 */
final class Preloader implements ApplicationInitializedListener {
  private static final Logger LOG = Logger.getInstance(Preloader.class);

  private final Executor myExecutor;
  private final ProgressIndicator myIndicator;
  private final ProgressIndicator myWrappingIndicator;

  Preloader() {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment() || !Registry.is("enable.activity.preloading")) {
      throw ExtensionNotApplicableException.INSTANCE;
    }

    myIndicator = new ProgressIndicatorBase();
    Disposer.register(app, () -> myIndicator.cancel());
    myExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Preloader Pool");
    myWrappingIndicator = new AbstractProgressIndicatorBase() {
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
  }

  private static void checkHeavyProcessRunning() {
    if (HeavyProcessLatch.INSTANCE.isRunning()) {
      TimeoutUtil.sleep(1);
    }
  }

  @Override
  public void componentsInitialized() {
    ProgressManager progressManager = ProgressManager.getInstance();
    PreloadingActivity.EP_NAME.processWithPluginDescriptor((activity, descriptor) -> {
      myExecutor.execute(() -> {
        if (myIndicator.isCanceled()) {
          return;
        }

        checkHeavyProcessRunning();
        if (myIndicator.isCanceled()) {
          return;
        }

        progressManager.runProcess(() -> {
          Activity measureActivity = StartUpMeasurer.startActivity(activity.getClass().getName(), ActivityCategory.PRELOAD_ACTIVITY,
                                                                   descriptor.getPluginId().getIdString());
          try {
            activity.preload(myWrappingIndicator);
          }
          catch (ProcessCanceledException ignore) {
            return;
          }
          measureActivity.end();
          if (LOG.isDebugEnabled()) {
            LOG.debug(activity.getClass().getName() + " finished");
          }
        }, myIndicator);
      });
    });
  }
}
