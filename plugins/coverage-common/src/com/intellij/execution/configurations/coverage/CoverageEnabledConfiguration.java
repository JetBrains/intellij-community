// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations.coverage;

import com.intellij.coverage.*;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Date;

/**
 * Base class for run configurations with enabled code coverage
 */
public abstract class CoverageEnabledConfiguration implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(CoverageEnabledConfiguration.class.getName());

  public static final Key<CoverageEnabledConfiguration> COVERAGE_KEY = Key.create("com.intellij.coverage");

  private static final @NonNls String COVERAGE_ENABLED_ATTRIBUTE_NAME = "enabled";
  @ApiStatus.Internal
  protected static final @NonNls String COVERAGE_RUNNER = "runner";
  private static final @NonNls String TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME = "per_test_coverage_enabled";
  private static final @NonNls String COVERAGE_TYPE_ATTRIBUTE_NAME = "sample_coverage";
  private static final @NonNls String TRACK_TEST_FOLDERS = "track_test_folders";

  private final RunConfigurationBase<?> myConfiguration;

  private boolean myIsCoverageEnabled = false;
  private String myRunnerId;
  private CoverageRunner myCachedRunner;
  private boolean myTrackTestFolders = false;

  private boolean myBranchCoverage = false;
  private boolean myTrackPerTestCoverage = false;
  @ApiStatus.Internal
  protected @NonNls String myCoverageFilePath;
  private CoverageSuite myCurrentCoverageSuite;

  /**
   *
   * @deprecated Use {@link CoverageEnabledConfiguration#CoverageEnabledConfiguration(RunConfigurationBase, CoverageRunner)}
   */
  @Deprecated
  public CoverageEnabledConfiguration(@NotNull RunConfigurationBase<?> configuration) {
    myConfiguration = configuration;
  }

  public CoverageEnabledConfiguration(@NotNull RunConfigurationBase<?> configuration, @NotNull CoverageRunner runner) {
    myConfiguration = configuration;
    setCoverageRunner(runner);
  }

  public @NotNull RunConfigurationBase<?> getConfiguration() {
    return myConfiguration;
  }

  public String getName() {
    return myConfiguration.getName();
  }

  @ApiStatus.Internal
  public @Nullable CoverageRunner getCoverageRunner() {
    if (myCachedRunner == null && myRunnerId != null) {
      myCachedRunner = CoverageRunner.getInstanceById(myRunnerId);
    }
    return myCachedRunner;
  }

  /**
   * Use {@link CoverageEnabledConfiguration#CoverageEnabledConfiguration(RunConfigurationBase, CoverageRunner)} when only one Runner
   * is available for this configuration
   */
  public void setCoverageRunner(@Nullable CoverageRunner coverageRunner) {
    myRunnerId = coverageRunner == null ? null : coverageRunner.getId();
    myCachedRunner = coverageRunner;
    myCoverageFilePath = null;
  }

  public boolean isTrackTestFolders() {
    return myTrackTestFolders;
  }

  public void setTrackTestFolders(boolean trackTestFolders) {
    myTrackTestFolders = trackTestFolders;
  }

  public CoverageSuite getCurrentCoverageSuite() {
    return myCurrentCoverageSuite;
  }

  public void setCurrentCoverageSuite(CoverageSuite currentCoverageSuite) {
    myCurrentCoverageSuite = currentCoverageSuite;
  }


  // These getter/setter methods are not used in platform code,
  // as these settings are stored in project level settings.
  // However, other implementations can reuse these methods.
  /**
   * @deprecated Moved to coverage settings.
   */
  @Deprecated
  public boolean isCoverageEnabled() {
    return myIsCoverageEnabled;
  }

  /**
   * @deprecated Moved to coverage settings.
   */
  @Deprecated
  public void setCoverageEnabled(final boolean isCoverageEnabled) {
    myIsCoverageEnabled = isCoverageEnabled;
  }

  /**
   * @deprecated Moved to coverage settings.
   */
  @Deprecated
  public boolean isBranchCoverageEnabled() {
    return myBranchCoverage;
  }

  /**
   * @deprecated Moved to coverage settings.
   */
  @Deprecated
  public void setBranchCoverage(final boolean branchCoverage) {
    myBranchCoverage = branchCoverage;
  }

  /**
   * @deprecated Moved to coverage settings.
   */
  @Deprecated
  public boolean isTrackPerTestCoverage() {
    return myTrackPerTestCoverage;
  }

  /**
   * @deprecated Moved to coverage settings.
   */
  @ApiStatus.Internal
  @Deprecated
  public void setTrackPerTestCoverage(final boolean testTracking) {
    myTrackPerTestCoverage = testTracking;
  }

  public String createSuiteName() {
    return CoverageBundle.message("coverage.results.suite.name", getName());
  }

  public CoverageFileProvider createFileProvider() {
    return new DefaultCoverageFileProvider(getCoverageFilePath());
  }

  public long createTimestamp() {
    return new Date().getTime();
  }

  @ApiStatus.Internal
  public void coverageRunnerExtensionRemoved(@NotNull CoverageRunner runner) {
    if (runner.getId().equals(myRunnerId)) {
      myConfiguration.putCopyableUserData(COVERAGE_KEY, null);
      myCachedRunner = null;
    }
  }

  @NonNls
  public @Nullable String getCoverageFilePath() {
    if (myCoverageFilePath == null) {
      myCoverageFilePath = createCoverageFile();
    }
    return myCoverageFilePath;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    // is enabled
    myIsCoverageEnabled = Boolean.parseBoolean(element.getAttributeValue(COVERAGE_ENABLED_ATTRIBUTE_NAME));

    // track per test coverage
    final String testTrackingAttribute = element.getAttributeValue(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME);
    myTrackPerTestCoverage = testTrackingAttribute != null && Boolean.parseBoolean(testTrackingAttribute);

    // line/branch coverage
    myBranchCoverage = !Boolean.parseBoolean(element.getAttributeValue(COVERAGE_TYPE_ATTRIBUTE_NAME, "true"));

    // track test folders
    final String trackTestFolders = element.getAttributeValue(TRACK_TEST_FOLDERS);
    myTrackTestFolders = trackTestFolders != null && Boolean.parseBoolean(trackTestFolders);

    // coverage runner
    final String runnerId = element.getAttributeValue(COVERAGE_RUNNER);
    if (runnerId != null) {
      myRunnerId = runnerId;
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    // enabled
    if (myIsCoverageEnabled) {
      element.setAttribute(COVERAGE_ENABLED_ATTRIBUTE_NAME, String.valueOf(true));
    }

    // per test
    if (myTrackPerTestCoverage) {
      element.setAttribute(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME, String.valueOf(true));
    }

    // line/branch coverage
    if (myBranchCoverage) {
      element.setAttribute(COVERAGE_TYPE_ATTRIBUTE_NAME, String.valueOf(false));
    }

    // test folders
    if (myTrackTestFolders) {
      element.setAttribute(TRACK_TEST_FOLDERS, String.valueOf(true));
    }

    // runner
    if (myRunnerId != null) {
      element.setAttribute(COVERAGE_RUNNER, myRunnerId);
    }
  }

  @NonNls
  protected @Nullable String createCoverageFile() {
    CoverageRunner runner = getCoverageRunner();
    if (runner == null) {
      return null;
    }

    Path coverageRootPath = Path.of(PathManager.getSystemPath(), "coverage");
    coverageRootPath.toFile().mkdirs();

    String projectName = FileUtil.sanitizeFileName(myConfiguration.getProject().getName());
    String configName = FileUtil.sanitizeFileName(myConfiguration.getName());
    String separator = coverageFileNameSeparator();
    String extension = runner.getDataFileExtension();
    String path = String.format("%s%s%s.%s", projectName, separator, configName, extension);

    return coverageRootPath.resolve(path).toString();
  }

  protected String coverageFileNameSeparator() {
    return "$";
  }

  public static boolean isApplicableTo(final @NotNull RunConfigurationBase<?> runConfiguration) {
    return getOrNull(runConfiguration) != null || getSuitableEngine(runConfiguration) != null;
  }

  @ApiStatus.Internal
  public static @Nullable CoverageEnabledConfiguration getOrNull(@NotNull RunConfigurationBase<?> runConfiguration) {
    return runConfiguration.getCopyableUserData(COVERAGE_KEY);
  }

  public static @NotNull CoverageEnabledConfiguration getOrCreate(final @NotNull RunConfigurationBase<?> runConfiguration) {
    CoverageEnabledConfiguration configuration = getOrNull(runConfiguration);
    if (configuration == null) {
      CoverageEngine suitableEngine = getSuitableEngine(runConfiguration);
      LOG.assertTrue(suitableEngine != null, "Coverage enabled run configuration wasn't found for run configuration: "
                                             + runConfiguration.getName() + ", type = " + runConfiguration.getClass().getName());
      configuration = suitableEngine.createCoverageEnabledConfiguration(runConfiguration);
      runConfiguration.putCopyableUserData(COVERAGE_KEY, configuration);
    }
    return configuration;
  }

  private static @Nullable CoverageEngine getSuitableEngine(@NotNull RunConfigurationBase<?> runConfiguration) {
    for (CoverageEngine engine : CoverageEngine.EP_NAME.getExtensionList()) {
      if (engine.isApplicableTo(runConfiguration)) {
        return engine;
      }
    }
    return null;
  }

  /**
   * @deprecated Is not used
   */
  @Deprecated
  public boolean canHavePerTestCoverage() {
    for (CoverageEngine engine : CoverageEngine.EP_NAME.getExtensions()) {
      if (engine.isApplicableTo(myConfiguration)) {
        return engine.canHavePerTestCoverage(myConfiguration);
      }
    }
    return false;
  }
}
