/*
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.execution.coverage;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.IDEACoverageRunner;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.*;
import com.intellij.execution.configurations.coverage.CoverageConfigurable;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CoverageRunConfigurationExtension extends RunConfigurationExtension {
  public void handleStartProcess(final ModuleBasedConfiguration configuration, OSProcessHandler handler) {
    handler.addProcessListener(new ProcessAdapter() {
      public void processTerminated(final ProcessEvent event) {
        final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(configuration.getProject());
        final CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.get(configuration);
        final CoverageSuite coverageSuite = coverageEnabledConfiguration.getCurrentCoverageSuite();
        if (coverageSuite != null) {
          coverageDataManager.coverageGathered(coverageSuite);
        }
      }
    });

  }

  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> SettingsEditor createEditor(T configuration) {
    return new CoverageConfigurable<T>(configuration);
  }

  public String getEditorTitle() {
    return "Code Coverage";
  }

  @Override
  public String getName() {
    return "coverage";
  }

  @Nullable
  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> Icon getIcon(T runConfiguration) {
    final CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.get(runConfiguration);
    if (coverageEnabledConfiguration.isCoverageEnabled()) {
      return CoverageEnabledConfiguration.WITH_COVERAGE_CONFIGURATION;
    }
    return null;
  }

  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> void updateJavaParameters(T configuration, JavaParameters params, RunnerSettings runnerSettings) {
    final CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.get(configuration);
    if ((!(runnerSettings.getData() instanceof DebuggingRunnerData) ||
         coverageEnabledConfiguration.getCoverageRunner() instanceof IDEACoverageRunner) &&
        coverageEnabledConfiguration.isCoverageEnabled()) {
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(configuration.getProject());
      coverageEnabledConfiguration.setCurrentCoverageSuite(coverageDataManager.addCoverageSuite(coverageEnabledConfiguration));
      coverageEnabledConfiguration.appendCoverageArgument(params);
    }
  }

  @Override
  public void readExternal(final ModuleBasedConfiguration runConfiguration, Element element) throws InvalidDataException {
    CoverageEnabledConfiguration.get(runConfiguration).readExternal(element);
  }

  @Override
  public void writeExternal(ModuleBasedConfiguration runConfiguration, Element element) throws WriteExternalException {
    CoverageEnabledConfiguration.get(runConfiguration).writeExternal(element);
  }

  @Override
  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> void patchConfiguration(T runJavaConfiguration) {
    CoverageEnabledConfiguration.get(runJavaConfiguration).setUpCoverageFilters(runJavaConfiguration.getRunClass(), runJavaConfiguration.getPackage());
  }

  @Override
  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> void checkConfiguration(T runJavaConfiguration)
    throws RuntimeConfigurationException {
    CoverageEnabledConfiguration configuration = CoverageEnabledConfiguration.get(runJavaConfiguration);
    if (configuration.isCoverageEnabled() && configuration.getCoverageRunner() == null) {
      throw new RuntimeConfigurationException("Coverage runner is not set");
    }
  }
}