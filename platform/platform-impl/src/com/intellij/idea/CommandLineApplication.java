// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class CommandLineApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.CommandLineApplication");

  protected static CommandLineApplication ourInstance;

  protected CommandLineApplication(boolean isInternal, boolean isUnitTestMode, boolean isHeadless, boolean isServer) {
    this(isInternal, isUnitTestMode, isHeadless, ApplicationManagerEx.IDEA_APPLICATION, isServer);
  }

  protected CommandLineApplication(boolean isInternal,
                                   boolean isUnitTestMode,
                                   boolean isHeadless,
                                   @NotNull @NonNls String appName,
                                   boolean isServer) {
    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = this;
    System.setProperty("idea.filewatcher.disabled", "" + !isServer);
    new ApplicationImpl(isInternal, isUnitTestMode, isHeadless, true, appName, isServer);
    ApplicationManagerEx.createApplication(isInternal, isUnitTestMode, isHeadless, true, appName, null);
  }

  public Object getData(@NotNull String dataId) {
    return null;
  }

  public static class MyDataManagerImpl extends DataManagerImpl {
    @Override
    @NotNull
    public DataContext getDataContext() {
      return new CommandLineDataContext();
    }

    @NotNull
    @Override
    public DataContext getDataContext(Component component) {
      return getDataContext();
    }

    @NotNull
    @Override
    public DataContext getDataContext(@NotNull Component component, int x, int y) {
      return getDataContext();
    }

    private static class CommandLineDataContext extends UserDataHolderBase implements DataContext {
      @Override
      public Object getData(@NotNull String dataId) {
        return ourInstance.getData(dataId);
      }
    }
  }
}
