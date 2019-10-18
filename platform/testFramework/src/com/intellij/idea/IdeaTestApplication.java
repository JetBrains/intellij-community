// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.HeadlessDataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.idea.TestAppLoaderKt.loadTestApp;

public final class IdeaTestApplication implements Disposable {
  private static volatile IdeaTestApplication ourInstance;
  private static volatile RuntimeException bootstrapError;
  private static volatile boolean isBootstrappingAppNow;

  private IdeaTestApplication() { }

  public void setDataProvider(@Nullable DataProvider provider) {
    getDataManager().setTestDataProvider(provider);
  }

  public void setDataProvider(@Nullable DataProvider provider, Disposable parentDisposable) {
    getDataManager().setTestDataProvider(provider, parentDisposable);
  }

  public @Nullable Object getData(@NotNull String dataId) {
    return getDataManager().getDataContext().getData(dataId);
  }

  private static HeadlessDataManager getDataManager() {
    return (HeadlessDataManager)ApplicationManager.getApplication().getComponent(DataManager.class);
  }

  public static IdeaTestApplication getInstance() {
    IdeaTestApplication instance = ourInstance;
    if (instance == null) {
      try {
        instance = createInstance();
      }
      catch (RuntimeException e) {
        bootstrapError = e;
        isBootstrappingAppNow = false;
        throw e;
      }
    }
    return instance;
  }

  @NotNull
  private static synchronized IdeaTestApplication createInstance() {
    if (ourInstance != null) {
      return ourInstance;
    }

    if (bootstrapError != null) {
      throw bootstrapError;
    }

    if (isBootstrappingAppNow) {
      throw new IllegalStateException("App bootstrap is already in process");
    }
    isBootstrappingAppNow = true;

    HeavyPlatformTestCase.doAutodetectPlatformPrefix();

    loadTestApp();

    isBootstrappingAppNow = false;
    ourInstance = new IdeaTestApplication();
    return ourInstance;
  }

  @Override
  public void dispose() {
    disposeInstance();
  }

  private static synchronized void disposeInstance() {
    if (ourInstance != null) {
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        // `ApplicationManager#ourApplication` will be automatically set to `null`
        Disposer.dispose(application);
      }
      ourInstance = null;
    }
  }
}