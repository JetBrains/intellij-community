package com.intellij.coverage;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public abstract class JavaCoverageRunner extends CoverageRunner {
  @Override
  public boolean acceptsCoverageProvider(@NotNull CoverageSupportProvider provider) {
    return provider instanceof JavaCoverageSupportProvider;
  }
}
