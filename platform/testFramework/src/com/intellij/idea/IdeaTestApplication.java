/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.idea;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.Nullable;

public class IdeaTestApplication extends CommandLineApplication implements Disposable {
  private DataProvider myDataContext;

  private IdeaTestApplication() {
    super(true, true, true);
  }

  public void setDataProvider(@Nullable DataProvider dataContext) {
    myDataContext = dataContext;
  }

  @Override
  @Nullable
  public Object getData(String dataId) {
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
