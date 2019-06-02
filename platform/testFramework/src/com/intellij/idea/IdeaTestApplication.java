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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class IdeaTestApplication implements Disposable {
  private static IdeaTestApplication ourInstance;

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

  public static synchronized IdeaTestApplication getInstance(@Nullable String configPath) {
    if (ourInstance == null) {
      PlatformTestCase.doAutodetectPlatformPrefix();
      ourInstance = new IdeaTestApplication();

      String[] args = {"inspect", "", "", ""};
      Main.setFlags(args);
      assert Main.isHeadless();
      assert Main.isCommandLine();
      System.setProperty(ApplicationImpl.IDEA_IS_UNIT_TEST, Boolean.TRUE.toString());
      IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
      ApplicationImpl.patchSystem();
      List<IdeaPluginDescriptor> loadedPlugins = PluginManagerCore.getLoadedPlugins();
      ApplicationImpl app = new ApplicationImpl(true, true, true, true, ApplicationManagerEx.IDEA_APPLICATION);
      ApplicationImpl.registerMessageBusListeners(app, loadedPlugins, true);
      app.registerComponents(loadedPlugins);
      RegistryKeyBean.addKeysFromPlugins();
      app.load(configPath, null);
      LoadingPhase.setCurrentPhase(LoadingPhase.FRAME_SHOWN);
    }

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