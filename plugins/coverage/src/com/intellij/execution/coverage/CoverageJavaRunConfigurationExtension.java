/*
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.execution.coverage;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.IDEACoverageRunner;
import com.intellij.coverage.listeners.CoverageListener;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.*;
import com.intellij.execution.configurations.coverage.CoverageConfigurable;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.execution.process.OSProcessHandler;
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
  public void handleStartProcess(final RunConfigurationBase configuration, OSProcessHandler handler) {
    if (!isApplicableFor(configuration)) {
      return;
    }

    CoverageDataManager.getInstance(configuration.getProject()).attachToProcess(handler, configuration);
  }

  @Nullable
  public SettingsEditor createEditor(RunConfigurationBase configuration) {
    if (!isApplicableFor(configuration)) {
      return null;
    }
    return new CoverageConfigurable(configuration);
  }

  public String getEditorTitle() {
    return CoverageEngine.getEditorTitle();
  }

  @Override
  public String getName() {
    return "coverage";
  }

  @Nullable
  public Icon getIcon(RunConfigurationBase runConfiguration) {
    if (!isApplicableFor(runConfiguration)) {
      return null;
    }
    return CoverageEngine.getIcon(runConfiguration);
  }

  public void updateJavaParameters(RunConfigurationBase configuration, JavaParameters params, RunnerSettings runnerSettings) {
    if (!isApplicableFor(configuration)) {
      return;
    }

    final JavaCoverageEnabledConfiguration coverageConfig = JavaCoverageEnabledConfiguration.getFrom(configuration);
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
  public void readExternal(final RunConfigurationBase runConfiguration, Element element) throws InvalidDataException {
     if (!isApplicableFor(runConfiguration)) {
      return;
    }

    //noinspection ConstantConditions
    JavaCoverageEnabledConfiguration.getFrom(runConfiguration).readExternal(element);
  }

  @Override
  public void writeExternal(RunConfigurationBase runConfiguration, Element element) throws WriteExternalException {
    if (!isApplicableFor(runConfiguration)) {
      return;
    }
    //noinspection ConstantConditions
    JavaCoverageEnabledConfiguration.getFrom(runConfiguration).writeExternal(element);
  }

  @Override
  public void patchConfiguration(RunConfigurationBase runJavaConfiguration) {
    if (!isApplicableFor(runJavaConfiguration)) {
      return;
    }
    final JavaCoverageEnabledConfiguration coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(runJavaConfiguration);
    assert coverageEnabledConfiguration != null;
    if (runJavaConfiguration instanceof CommonJavaRunConfigurationParameters) {
      coverageEnabledConfiguration.setUpCoverageFilters(((CommonJavaRunConfigurationParameters)runJavaConfiguration).getRunClass(),
                                                        ((CommonJavaRunConfigurationParameters)runJavaConfiguration).getPackage());
    }
  }

  @Override
  public void checkConfiguration(RunConfigurationBase runJavaConfiguration)
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


  @Override
  public boolean isListenerDisabled(RunConfigurationBase configuration, Object listener) {
    if (listener instanceof CoverageListener) {
      final CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.getOrCreate(configuration);
      return !(coverageEnabledConfiguration.isCoverageEnabled() && coverageEnabledConfiguration.getCoverageRunner() instanceof IDEACoverageRunner);
    }
    return false;
  }

  private static boolean isApplicableFor(final RunConfigurationBase configuration) {
    return JavaCoverageEnabledConfiguration.isApplicableTo(configuration);
  }
}