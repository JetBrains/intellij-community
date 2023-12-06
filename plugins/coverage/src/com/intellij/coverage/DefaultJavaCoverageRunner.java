// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.execution.configurations.ConfigurationInfoProvider;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import org.jetbrains.annotations.NotNull;

public final class DefaultJavaCoverageRunner extends DefaultJavaProgramRunner {
  @Override
  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    try {
      return executorId.equals(CoverageExecutor.EXECUTOR_ID) &&
             //profile instanceof ModuleBasedConfiguration &&
             !(profile instanceof RunConfigurationWithSuppressedDefaultRunAction) &&
             profile instanceof RunConfigurationBase<?> runConfiguration &&
             CoverageEngine.EP_NAME.findExtensionOrFail(JavaCoverageEngine.class).isApplicableTo(runConfiguration);
    }
    catch (Exception e) {
      return false;
    }
  }

  @Override
  public RunnerSettings createConfigurationData(@NotNull ConfigurationInfoProvider settingsProvider) {
    return new CoverageRunnerData();
  }

  @NotNull
  @Override
  public String getRunnerId() {
    return "Cover";
  }
}
