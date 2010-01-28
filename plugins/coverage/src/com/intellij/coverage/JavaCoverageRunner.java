package com.intellij.coverage;

import com.intellij.execution.configurations.SimpleJavaParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public abstract class JavaCoverageRunner extends CoverageRunner {
  @Override
  public boolean acceptsCoverageProvider(@NotNull CoverageSupportProvider provider) {
    return provider instanceof JavaCoverageSupportProvider;
  }

  public abstract void appendCoverageArgument(final String sessionDataFilePath, @Nullable final String[] patterns, final SimpleJavaParameters parameters,
                                              final boolean collectLineInfo, final boolean isSampling);

}
