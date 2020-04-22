// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.application.options.RegistryManager;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;

final class Preloader implements ApplicationInitializedListener {
  private static final ExtensionPointName<PreloadingActivity> EP_NAME = new ExtensionPointName<>("com.intellij.preloadingActivity");
  private static final Logger LOG = Logger.getInstance(Preloader.class);

  private static void checkHeavyProcessRunning() {
    if (HeavyProcessLatch.INSTANCE.isRunning()) {
      TimeoutUtil.sleep(1);
    }
  }

  @Override
  public void componentsInitialized() {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment() || !RegistryManager.getInstance().is("enable.activity.preloading")) {
      return;
    }

    EP_NAME.processWithPluginDescriptor(Preloader::preload);
  }

  private static void preload(@NotNull PreloadingActivity activity, @Nullable PluginDescriptor descriptor) {
    ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Preloader Pool", 1);

    ProgressIndicator indicator = new ProgressIndicatorBase();
    Disposer.register(ApplicationManager.getApplication(), indicator::cancel);
    ProgressIndicator wrappingIndicator = new AbstractProgressIndicatorBase() {
      @Override
      public void checkCanceled() {
        checkHeavyProcessRunning();
        indicator.checkCanceled();
      }

      @Override
      public boolean isCanceled() {
        return indicator.isCanceled();
      }
    };

    executor.execute(() -> {
      if (indicator.isCanceled()) {
        return;
      }

      checkHeavyProcessRunning();
      if (indicator.isCanceled()) {
        return;
      }

      ProgressManager.getInstance().runProcess(() -> {
        Activity measureActivity;
        if (descriptor == null) {
          measureActivity = null;
        }
        else {
          measureActivity = StartUpMeasurer.startActivity(activity.getClass().getName(), ActivityCategory.PRELOAD_ACTIVITY, descriptor.getPluginId().getIdString());
        }

        try {
          activity.preload(wrappingIndicator);
        }
        catch (ProcessCanceledException ignore) {
          return;
        }
        finally {
          if (measureActivity != null) {
            measureActivity.end();
          }
        }

        if (LOG.isDebugEnabled()) {
          LOG.debug(activity.getClass().getName() + " finished");
        }
      }, indicator);
    });
  }
}
