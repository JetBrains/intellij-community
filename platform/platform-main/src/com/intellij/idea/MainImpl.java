// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletionStage;

@SuppressWarnings({"UnusedDeclaration"})
public final class MainImpl implements StartupUtil.AppStarter {
  public MainImpl() {
    PlatformUtils.setDefaultPrefixForCE();
  }

  @Override
  public void start(@NotNull List<String> args, @NotNull CompletionStage<?> initUiTask) {
    ApplicationLoader.initApplication(args, initUiTask);
  }

  @Override
  public void startupWizardFinished(@NotNull CustomizeIDEWizardStepsProvider provider) {
    ApplicationLoader.setWizardStepsProvider(provider);
  }
}