// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Represents coverage framework inside IntelliJ.
 */
public abstract class CoverageRunner {
  private static final Logger LOG = Logger.getInstance(CoverageRunner.class);
  public static final ExtensionPointName<CoverageRunner> EP_NAME = ExtensionPointName.create("com.intellij.coverageRunner");

  /**
   * Loads coverage data from {@code sessionDataFile} into IntelliJ presentation, {@link ProjectData}.
   * 
   * @param baseCoverageSuite suite where coverage would be loaded. 
   *                          Can be used to retrieve additional information about configuration which was run with coverage.
   */
  @ApiStatus.NonExtendable
  public @Nullable ProjectData loadCoverageData(final @NotNull File sessionDataFile, final @Nullable CoverageSuite baseCoverageSuite) {
    if (baseCoverageSuite == null) {
      return loadCoverageDataWithLogging(sessionDataFile, null, new DummyCoverageLoadErrorReporter()).getProjectData();
    }
    CoverageLoadListener listener = baseCoverageSuite.getProject().getMessageBus().syncPublisher(CoverageLoadListener.COVERAGE_TOPIC);
    LoadCoverageResult result;
    listener.coverageLoadingStarted(sessionDataFile);
    try {
      result = loadCoverageDataWithLogging(sessionDataFile, baseCoverageSuite, new CoverageLoadErrorReporterImplementation(listener, sessionDataFile));
    }
    catch (UnsupportedOperationException e) {
      LOG.warn(e);
      String message = "Coverage runner " + this.getClass().getName() + " does not implement coverage loading, please contact a developer of extension";
      result = new FailedLoadCoverageResult(message, e);
    }
    catch (Exception e) {
      if (e instanceof ControlFlowException) throw e;
      LOG.warn(e);
      result = new FailedLoadCoverageResult(e, true);
    }
    listener.reportCoverageLoaded(result, sessionDataFile);

    return result.getProjectData();
  }

  /**
   * Loads coverage data from {@code sessionDataFile} into IntelliJ presentation {@link ProjectData},
   * with a result of execution {@link LoadCoverageResult}.
   *
   * @param baseCoverageSuite suite where coverage would be loaded.
   *                          Can be used to retrieve additional information about configuration which was run with coverage.
   * @param reporter wrapper around {@link CoverageLoadListener} is used for notifying about errors during coverage loading
   */
  @ApiStatus.OverrideOnly
  public @NotNull LoadCoverageResult loadCoverageDataWithLogging(
    final @NotNull File sessionDataFile,
    final @Nullable CoverageSuite baseCoverageSuite,
    final @NotNull CoverageLoadErrorReporter reporter
  ) {
    throw new UnsupportedOperationException("Method loadCoverageDataWithLogging should be implemented");
  }

  /**
   * When multiple coverage runners are available for one {@link CoverageEngine}, 
   * {@code getPresentableName()} is used to render coverage runner in UI.
   */
  public abstract @NotNull @NonNls String getPresentableName();

  /**
   * @return unique id to serialize/deserialize used coverage runner.
   */
  public abstract @NotNull @NonNls String getId();

  /**
   * Used to compose file name where coverage framework should save coverage data.
   * It is also used to check if runner can load data from disk without actual loading.
   * 
   * @return file extension of the file where coverage framework stores coverage data.
   */
  public abstract @NotNull @NonNls String getDataFileExtension();

  public @NonNls String @NotNull [] getDataFileExtensions() {
    return new String[]{getDataFileExtension()};
  }

  /**
   * Checks whether a file is supported by the runner.
   */
  public boolean canBeLoaded(@NotNull File candidate) {
    return true;
  }

  /**
   * @return true if coverage runner works with the languages which corresponds to {@link CoverageEngine}.
   */
  public abstract boolean acceptsCoverageEngine(final @NotNull CoverageEngine engine);

  public static <T extends CoverageRunner> T getInstance(@NotNull Class<T> coverageRunnerClass) {
    for (CoverageRunner coverageRunner : EP_NAME.getExtensionList()) {
      if (coverageRunnerClass.isInstance(coverageRunner)) {
        return coverageRunnerClass.cast(coverageRunner);
      }
    }
    assert false;
    return null;
  }

  /**
   * @return true if coverage framework can collect coverage information per test. 
   *         Then IntelliJ would allow e.g., seeing what tests cover selected line.
   */
  @ApiStatus.Internal
  public boolean isCoverageByTestApplicable() {
    return false;
  }

  @ApiStatus.Internal
  public static @Nullable CoverageRunner getInstanceById(@NotNull String id) {
    for (CoverageRunner coverageRunner : EP_NAME.getExtensionList()) {
      if (coverageRunner.getId().equals(id)) {
        return coverageRunner;
      }
    }
    return null;
  }
}