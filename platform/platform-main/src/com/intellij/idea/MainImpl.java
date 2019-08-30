// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;

@SuppressWarnings({"UnusedDeclaration"})
public final class MainImpl {
  private MainImpl() { }

  /**
   * Called from PluginManager via reflection.
   */
  public static void start(@NotNull String[] args) throws Exception {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.getPlatformPrefix(PlatformUtils.IDEA_CE_PREFIX));

    StartupUtil.prepareAndStart(args, new StartupUtil.AppStarter() {
      @Override
      public void start(@NotNull Future<?> initUiTask) {
        ApplicationLoader.initApplication(args, initUiTask);
      }

      @Override
      public void startupWizardFinished(@NotNull CustomizeIDEWizardStepsProvider provider) {
        ApplicationLoader.setWizardStepsProvider(provider);
      }
    });
  }
}