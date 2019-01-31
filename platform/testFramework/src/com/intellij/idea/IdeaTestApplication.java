// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdeaTestApplication extends CommandLineApplication implements Disposable {
  private DataProvider myDataContext;

  private IdeaTestApplication() {
    super(true, true, true);
  }

  public void setDataProvider(@Nullable DataProvider dataContext) {
    myDataContext = dataContext;
  }

  public void setDataProvider(@Nullable DataProvider dataContext, Disposable parentDisposable) {
    DataProvider oldDataContext = myDataContext;
    myDataContext = dataContext;
    Disposer.register(parentDisposable, () -> myDataContext = oldDataContext);
  }

  @Override
  @Nullable
  public Object getData(@NotNull String dataId) {
    return myDataContext == null ? null : myDataContext.getData(dataId);
  }

  public static IdeaTestApplication getInstance() {
    return getInstance(null);
  }

  public static synchronized IdeaTestApplication getInstance(@Nullable final String configPath) {
    if (ourInstance == null) {
      PlatformTestCase.doAutodetectPlatformPrefix();
      new IdeaTestApplication();
      PluginManagerCore.getPlugins();
      ApplicationManagerEx.getApplicationEx().load(configPath);
    }
    return (IdeaTestApplication)ourInstance;
  }

  @Override
  public void dispose() {
    disposeInstance();
  }

  private static void disposeInstance() {
    if (ourInstance == null) return;
    Application applicationEx = ApplicationManager.getApplication();
    if (applicationEx != null) {
      Disposer.dispose(applicationEx);
      //ApplicationManagerEx.setApplication(null); it will set automatically back to null
    }
    ourInstance = null;
  }
}
