package com.intellij.coverage;

import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

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

  @Nullable
  @Override
  public CoverageSuite createCoverageSuite(@NotNull final CoverageRunner covRunner,
                                           @NotNull final String name,
                                           @NotNull final CoverageFileProvider coverageDataFileProvider,
                                           String[] filters,
                                           long lastCoverageTimeStamp,
                                           String suiteToMerge,
                                           boolean coverageByTestEnabled,
                                           boolean tracingEnabled,
                                           boolean trackTestFolders) {
    if (covRunner.acceptsCoverageProvider(this)) {
      return createSuite(covRunner, name, coverageDataFileProvider, filters, lastCoverageTimeStamp, suiteToMerge, coverageByTestEnabled,
                         tracingEnabled, trackTestFolders);
    }
    return null;
  }

  @Override
  public CoverageSuite createCoverageSuite(@NotNull final CoverageRunner covRunner,
                                           @NotNull final String name,
                                           @NotNull final CoverageFileProvider fileProvider,
                                           @NotNull final CoverageEnabledConfiguration config) {
    if (config instanceof JavaCoverageEnabledConfiguration && covRunner.acceptsCoverageProvider(this)) {
      final JavaCoverageEnabledConfiguration javaConfig = (JavaCoverageEnabledConfiguration)config;
      return createSuite(covRunner, name, fileProvider,
                         javaConfig.getPatterns(),
                         new Date().getTime(),
                         javaConfig.getSuiteToMergeWith(),
                         javaConfig.isTrackPerTestCoverage() && !javaConfig.isSampling(),
                         !javaConfig.isSampling(),
                         javaConfig.isTrackTestFolders());
    }
    return null;
  }

  @Nullable
  @Override
  public CoverageSuite createEmptyCoverageSuite(@NotNull CoverageRunner coverageRunner) {
    if (coverageRunner.acceptsCoverageProvider(this)) {
      return new JavaCoverageSuite(this);
    }
    return null;
  }

  @NotNull
  @Override
  public CoverageAnnotator getCoverageAnnotator(Project project) {
    return JavaCoverageAnnotator.getInstance(project);
  }

  private JavaCoverageSuite createSuite(CoverageRunner acceptedCovRunner,
                                        String name, CoverageFileProvider coverageDataFileProvider,
                                        String[] filters,
                                        long lastCoverageTimeStamp,
                                        String suiteToMerge,
                                        boolean coverageByTestEnabled,
                                        boolean tracingEnabled,
                                        boolean trackTestFolders) {
    return new JavaCoverageSuite(name, coverageDataFileProvider, filters, lastCoverageTimeStamp, suiteToMerge, coverageByTestEnabled, tracingEnabled,
                                 trackTestFolders, acceptedCovRunner, this);
  }
}
