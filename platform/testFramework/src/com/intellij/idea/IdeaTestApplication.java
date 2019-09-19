// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.HeadlessDataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.ui.IconManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    String[] args = {"inspect", "", "", ""};
    Main.setFlags(args);
    assert Main.isHeadless();
    assert Main.isCommandLine();
    PluginManagerCore.isUnitTestMode = true;
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);

    CompletableFuture<List<IdeaPluginDescriptor>> loadedPluginFuture = CompletableFuture.supplyAsync(() -> {
      return PluginManagerCore.getLoadedPlugins(IdeaTestApplication.class.getClassLoader());
    }, AppExecutorUtil.getAppExecutorService());

    StartupUtil.replaceSystemEventQueue(Logger.getInstance(IdeaTestApplication.class));
    ApplicationImpl app = new ApplicationImpl(true, true, true, true);
    IconManager.activate();
    List<IdeaPluginDescriptorImpl> plugins;
    try {
      plugins = ApplicationLoader.registerRegistryAndInitStore(ApplicationLoader.registerAppComponents(loadedPluginFuture, app), app)
        .get(20, TimeUnit.SECONDS);
    }
    catch (TimeoutException e) {
      throw new RuntimeException("Cannot load plugin descriptors in 20 seconds: " + ThreadDumper.dumpThreadsToString(), e);
    }
    catch (ExecutionException | InterruptedException e) {
      Throwable t = e.getCause() == null ? e : e.getCause();
      ExceptionUtil.rethrowUnchecked(t);
      throw new RuntimeException(t);
    }

    CompletableFuture<Void> preloadServiceFuture = ApplicationLoader.preloadServices(plugins, app, "");
    app.loadComponents(null);
    try {
      preloadServiceFuture.get(20, TimeUnit.SECONDS);
    }
    catch (TimeoutException e) {
      throw new RuntimeException("Cannot preload services in 20 seconds: " + ThreadDumper.dumpThreadsToString(), e);
    }
    catch (ExecutionException | InterruptedException e) {
      ExceptionUtil.rethrow(e.getCause() == null ? e : e.getCause());
    }

    ApplicationLoader.callAppInitialized(app);

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