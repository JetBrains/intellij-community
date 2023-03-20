// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * part of GradleJvmSuppportMatrix, extracted for better test-ability
 */
@ApiStatus.Internal
public class CompatibilitySupportUpdater {
  private static final Logger LOG = Logger.getInstance(CompatibilitySupportUpdater.class);

  @NotNull
  public Future<?> checkForUpdates() {
    String сonfigUrl = Registry.stringValue("gradle.compatibility.config.url");
    int updateInterval = Registry.intValue("gradle.compatibility.update.interval");
    if (updateInterval == 0 || StringUtil.isEmpty(сonfigUrl)) {
      return CompletableFuture.completedFuture(null);
    }
    return ApplicationManager.getApplication().executeOnPooledThread(() -> {
      JvmCompatibilityState state = GradleJvmSupportMatrix.getInstance().getState();
      long lastUpdateTime = state == null ? 0 : state.lastUpdateTime;
      if (lastUpdateTime + TimeUnit.DAYS.toMillis(updateInterval) > System.currentTimeMillis()) {
        return;
      }
      retrieveNewData(сonfigUrl);
    });
  }

  private void retrieveNewData(String сonfigUrl) {
    try {
      String json = HttpRequests.request(сonfigUrl)
        .forceHttps(!ApplicationManager.getApplication().isUnitTestMode())
        .productNameAsUserAgent()
        .readString();
      GradleJvmSupportMatrix.getInstance().setStateAsString(json);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }


  public static CompatibilitySupportUpdater getInstance() {
    return ApplicationManager.getApplication().getService(CompatibilitySupportUpdater.class);
  }
}
