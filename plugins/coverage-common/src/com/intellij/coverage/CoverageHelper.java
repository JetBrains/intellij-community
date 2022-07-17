// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class CoverageHelper {
  private CoverageHelper() {
  }

  public static void attachToProcess(@NotNull RunConfigurationBase configuration,
                                     @NotNull ProcessHandler handler,
                                     RunnerSettings runnerSettings) {
    resetCoverageSuit(configuration);

    // attach to process termination listener
    CoverageDataManager.getInstance(configuration.getProject()).attachToProcess(handler, configuration, runnerSettings);
  }

  public static void resetCoverageSuit(RunConfigurationBase configuration) {
    final CoverageEnabledConfiguration covConfig = CoverageEnabledConfiguration.getOrCreate(configuration);

    // reset coverage suite
    covConfig.setCurrentCoverageSuite(null);

    // register new coverage suite
    Project project = configuration.getProject();
    ApplicationManager.getApplication().invokeAndWait(() -> covConfig.setCurrentCoverageSuite(CoverageDataManager.getInstance(project).addCoverageSuite(covConfig)), 
                                                      ModalityState.NON_MODAL);
  }

  public static void doReadExternal(RunConfigurationBase runConfiguration, Element element) throws InvalidDataException {
    final CoverageEnabledConfiguration covConf = CoverageEnabledConfiguration.getOrCreate(runConfiguration);

    covConf.readExternal(element);
  }

  public static void doWriteExternal(RunConfigurationBase runConfiguration, Element element) {
    CoverageEnabledConfiguration.getOrCreate(runConfiguration).writeExternal(element);
  }
}
