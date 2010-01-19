/*
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.execution.coverage;

import com.intellij.coverage.*;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.*;
import com.intellij.execution.configurations.coverage.CoverageConfigurable;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Registers "Coverage" tab in Java run configurations
 */
public class CoverageJavaRunConfigurationExtension extends RunConfigurationExtension {
  public void handleStartProcess(final ModuleBasedConfiguration configuration, OSProcessHandler handler) {
    if (!isApplicableFor(configuration)) {
      return;
    }

    final CoverageEnabledConfiguration coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(configuration);
    //noinspection ConstantConditions
    if (coverageEnabledConfiguration.isCoverageEnabled()) {
      handler.addProcessListener(new ProcessAdapter() {
        public void processTerminated(final ProcessEvent event) {
          final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(configuration.getProject());
          final CoverageEnabledConfiguration coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(configuration);
          //noinspection ConstantConditions
          final CoverageSuite coverageSuite = coverageEnabledConfiguration.getCurrentCoverageSuite();
          if (coverageSuite != null) {
            coverageDataManager.coverageGathered(coverageSuite);
          }
        }
      });
    }
  }

  @Nullable
  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> SettingsEditor createEditor(T configuration) {
    if (!isApplicableFor(configuration)) {
      return null;
    }
    return new CoverageConfigurable<T>(configuration);
  }

  public String getEditorTitle() {
    return CoverageSupportProvider.getEditorTitle();
  }

  @Override
  public String getName() {
    return "coverage";
  }

  @Nullable
  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> Icon getIcon(T runConfiguration) {
    if (!isApplicableFor(runConfiguration)) {
      return null;
    }
    return CoverageSupportProvider.getIcon(runConfiguration);
  }

  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> void updateJavaParameters(T configuration, JavaParameters params, RunnerSettings runnerSettings) {
    if (!isApplicableFor(configuration)) {
      return;
    }

    final CoverageEnabledConfiguration coverageConfig = JavaCoverageEnabledConfiguration.getFrom(configuration);
    //noinspection ConstantConditions
    coverageConfig.setCurrentCoverageSuite(null);
    if ((!(runnerSettings.getData() instanceof DebuggingRunnerData) || coverageConfig.getCoverageRunner() instanceof IDEACoverageRunner)
        && coverageConfig.isCoverageEnabled()
        && coverageConfig.getCoverageRunner() != null) {
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(configuration.getProject());
      coverageConfig.setCurrentCoverageSuite(coverageDataManager.addCoverageSuite(coverageConfig));
      coverageConfig.appendCoverageArgument(params);
    }
  }

  @Override
  public void readExternal(final ModuleBasedConfiguration runConfiguration, Element element) throws InvalidDataException {
    if (!isApplicableFor(runConfiguration)) {
      return;
    }

    //noinspection ConstantConditions
    JavaCoverageEnabledConfiguration.getFrom(runConfiguration).readExternal(element);
  }

  @Override
  public void writeExternal(ModuleBasedConfiguration runConfiguration, Element element) throws WriteExternalException {
    if (!isApplicableFor(runConfiguration)) {
      return;
    }
    //noinspection ConstantConditions
    JavaCoverageEnabledConfiguration.getFrom(runConfiguration).writeExternal(element);
  }

  @Override
  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> void patchConfiguration(T runJavaConfiguration) {
    if (!isApplicableFor(runJavaConfiguration)) {
      return;
    }
    final JavaCoverageEnabledConfiguration coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(runJavaConfiguration);
    assert coverageEnabledConfiguration != null;
    coverageEnabledConfiguration.setUpCoverageFilters(runJavaConfiguration.getRunClass(), runJavaConfiguration.getPackage());
  }

  @Override
  public <T extends ModuleBasedConfiguration & RunJavaConfiguration> void checkConfiguration(T runJavaConfiguration)
    throws RuntimeConfigurationException {
    if (!isApplicableFor(runJavaConfiguration)) {
      return;
    }

    final CoverageEnabledConfiguration configuration = JavaCoverageEnabledConfiguration.getFrom(runJavaConfiguration);
    assert configuration != null;
    if (configuration.isCoverageEnabled() && configuration.getCoverageRunner() == null) {
      throw new RuntimeConfigurationException("Coverage runner is not set");
    }
  }

  private boolean isApplicableFor(final ModuleBasedConfiguration configuration) {
    return JavaCoverageEnabledConfiguration.getFrom(configuration) != null;
  }
}