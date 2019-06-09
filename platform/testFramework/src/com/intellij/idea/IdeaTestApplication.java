// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.diagnostic.LoadingPhase;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.HeadlessDataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.RegistryKeyBean;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.ui.IconManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    return getInstance(null);
  }

  public static IdeaTestApplication getInstance(@Nullable String configPath) {
    IdeaTestApplication instance = ourInstance;
    if (instance == null) {
      try {
        instance = createInstance(configPath);
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
  private static synchronized IdeaTestApplication createInstance(@Nullable String configPath) {
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

    PlatformTestCase.doAutodetectPlatformPrefix();

    String[] args = {"inspect", "", "", ""};
    Main.setFlags(args);
    assert Main.isHeadless();
    assert Main.isCommandLine();
    PluginManagerCore.isUnitTestMode = true;
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);

    Future<List<IdeaPluginDescriptor>> loadedPluginFuture = AppExecutorUtil.getAppExecutorService().submit(() -> PluginManagerCore.getLoadedPlugins());
    ApplicationImpl.patchSystem();
    ApplicationImpl app = new ApplicationImpl(true, true, true, true, ApplicationManagerEx.IDEA_APPLICATION);
    IconManager.activate();
    List<IdeaPluginDescriptor> loadedPlugins = null;
    try {
      loadedPlugins = loadedPluginFuture.get(5, TimeUnit.SECONDS);
    }
    catch (ExecutionException e) {
      ExceptionUtil.rethrow(e.getCause() == null ? e : e.getCause());
    }
    catch (Exception e) {
      ExceptionUtil.rethrow(e);
    }
    ApplicationImpl.registerMessageBusListeners(app, loadedPlugins, true);
    app.registerComponents(loadedPlugins);
    RegistryKeyBean.addKeysFromPlugins();
    app.load(configPath, null);
    LoadingPhase.setCurrentPhase(LoadingPhase.FRAME_SHOWN);

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
        Disposer.dispose(application);  // `ApplicationManager#ourApplication` will be automatically set to `null`
      }
      ourInstance = null;
    }
  }
}