/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class CommandLineApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.CommandLineApplication");

  protected static CommandLineApplication ourInstance;

  static {
    System.setProperty("idea.filewatcher.disabled", "true");
  }

  protected CommandLineApplication(boolean isInternal, boolean isUnitTestMode, boolean isHeadless) {
    this(isInternal, isUnitTestMode, isHeadless, ApplicationManagerEx.IDEA_APPLICATION);
  }

  protected CommandLineApplication(boolean isInternal, boolean isUnitTestMode, boolean isHeadless, @NotNull @NonNls String appName) {
    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = this;
    ApplicationManagerEx.createApplication(isInternal, isUnitTestMode, isHeadless, true, appName, null);
  }

  public Object getData(String dataId) {
    return null;
  }

  public static class MyDataManagerImpl extends DataManagerImpl {
    @Override
    @NotNull
    public DataContext getDataContext() {
      return new CommandLineDataContext();
    }

    @Override
    public DataContext getDataContext(Component component) {
      return getDataContext();
    }

    @Override
    public DataContext getDataContext(@NotNull Component component, int x, int y) {
      return getDataContext();
    }

    private static class CommandLineDataContext extends UserDataHolderBase implements DataContext {
      @Override
      public Object getData(String dataId) {
        return ourInstance.getData(dataId);
      }
    }
  }
}
