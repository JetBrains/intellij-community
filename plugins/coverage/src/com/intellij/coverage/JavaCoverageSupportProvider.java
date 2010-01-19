package com.intellij.coverage;

import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;

/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageSupportProvider extends CoverageSupportProvider {
  @Override
  public CoverageEnabledConfiguration createCoverageEnabledConfiguration(final ModuleBasedConfiguration conf) {
    if (conf instanceof RunJavaConfiguration) {
      return new JavaCoverageEnabledConfiguration(conf);
    }
    return null;
  }
}
