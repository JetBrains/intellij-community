// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

import static com.intellij.openapi.diagnostic.LoggerKt.rethrowControlFlowException;

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
   * @deprecated Override {@link CoverageRunner#loadCoverageData(Path, CoverageSuite, CoverageLoadErrorReporter)}
   */
  @SuppressWarnings({"IO_FILE_USAGE", "unused"})
  @Deprecated
  public @Nullable ProjectData loadCoverageData(final @NotNull File sessionDataFile, final @Nullable CoverageSuite baseCoverageSuite) {
    throw new IllegalStateException("Should not be called, please override loadCoverageDataWithLogging");
  }

  /**
   * @deprecated Use {@link #loadCoverageDataWithReporting(Path, CoverageSuite)} instead.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public final @Nullable ProjectData loadCoverageDataWithReporting(
    final @NotNull File sessionDataFile,
    final @Nullable CoverageSuite baseCoverageSuite
  ) {
    return loadCoverageDataWithReporting(sessionDataFile.toPath(), baseCoverageSuite);
  }

  public final @Nullable ProjectData loadCoverageDataWithReporting(
    final @NotNull Path sessionDataFile,
    final @Nullable CoverageSuite baseCoverageSuite
  ) {
    if (baseCoverageSuite == null) {
      return loadCoverageData(sessionDataFile, null, new DummyCoverageLoadErrorReporter()).getProjectData();
    }
    CoverageLoadingListener listener = baseCoverageSuite.getProject().getMessageBus().syncPublisher(CoverageLoadingListener.COVERAGE_TOPIC);
    CoverageLoadingResult result;
    listener.coverageLoadingStarted(sessionDataFile);
    try {
      result =
        loadCoverageData(sessionDataFile, baseCoverageSuite, new CoverageLoadErrorReporterImplementation(listener, sessionDataFile));
    }
    catch (Exception e) {
      rethrowControlFlowException(e);
      LOG.warn(e);
      result = new FailedCoverageLoadingResult(e, true);
    }
    listener.reportCoverageLoaded(result, sessionDataFile);

    return result.getProjectData();
  }

  /**
   * Loads coverage data from {@code sessionDataFile} into IntelliJ presentation {@link ProjectData},
   * with a result of execution {@link CoverageLoadingResult}.
   *
   * @param baseCoverageSuite suite where coverage would be loaded.
   *                          Can be used to retrieve additional information about configuration which was run with coverage.
   * @param reporter          wrapper around {@link CoverageLoadingListener} is used for notifying about errors during coverage loading
   * @deprecated Override {@link #loadCoverageData(Path, CoverageSuite, CoverageLoadErrorReporter)} instead.
   */
  @SuppressWarnings({"IO_FILE_USAGE", "unused"})
  @Deprecated
  @ApiStatus.OverrideOnly
  protected @NotNull CoverageLoadingResult loadCoverageData(
    final @NotNull File sessionDataFile,
    final @Nullable CoverageSuite baseCoverageSuite,
    final @NotNull CoverageLoadErrorReporter reporter
  ) {
    ProjectData data = loadCoverageData(sessionDataFile, baseCoverageSuite);
    if (data == null) {
      return new FailedCoverageLoadingResult("Couldn't load coverage data");
    }
    else {
      return new SuccessCoverageLoadingResult(data);
    }
  }

  /**
   * Loads coverage data from {@code sessionDataFile} into IntelliJ presentation {@link ProjectData},
   * with a result of execution {@link CoverageLoadingResult}.
   *
   * @param baseCoverageSuite suite where coverage would be loaded.
   *                          Can be used to retrieve additional information about configuration which was run with coverage.
   * @param reporter          wrapper around {@link CoverageLoadingListener} is used for notifying about errors during coverage loading
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @ApiStatus.OverrideOnly
  protected @NotNull CoverageLoadingResult loadCoverageData(
    final @NotNull Path sessionDataFile,
    final @Nullable CoverageSuite baseCoverageSuite,
    final @NotNull CoverageLoadErrorReporter reporter
  ) {
    return loadCoverageData(sessionDataFile.toFile(), baseCoverageSuite, reporter);
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
   *
   * @deprecated Override {@link #canBeLoaded(Path)} instead.
   */
  @SuppressWarnings({"IO_FILE_USAGE", "unused"})
  @Deprecated
  public boolean canBeLoaded(@NotNull File candidate) {
    return true;
  }

  /**
   * Checks whether a file is supported by the runner.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  public boolean canBeLoaded(@NotNull Path candidate) {
    return canBeLoaded(candidate.toFile());
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
   * Then IntelliJ would allow e.g., seeing what tests cover selected line.
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
