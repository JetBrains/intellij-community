// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations.coverage;

import com.intellij.coverage.*;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
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

  protected static final @NonNls String COVERAGE_ENABLED_ATTRIBUTE_NAME = "enabled";
  protected static final @NonNls String COVERAGE_RUNNER = "runner";
  protected static final @NonNls String TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME = "per_test_coverage_enabled";
  protected static final @NonNls String COVERAGE_TYPE_ATTRIBUTE_NAME = "sample_coverage";
  protected static final @NonNls String TRACK_TEST_FOLDERS = "track_test_folders";

  private final RunConfigurationBase<?> myConfiguration;

  private boolean myIsCoverageEnabled = false;
  private CoverageRunner myCoverageRunner;
  private boolean myTrackTestFolders = false;

  private boolean myBranchCoverage = false;
  private boolean myTrackPerTestCoverage = false;

  protected @NonNls String myCoverageFilePath;
  private CoverageSuite myCurrentCoverageSuite;

  public CoverageEnabledConfiguration(@NotNull RunConfigurationBase<?> configuration) {
    myConfiguration = configuration;
  }

  public @NotNull RunConfigurationBase<?> getConfiguration() {
    return myConfiguration;
  }

  public String getName() {
    return myConfiguration.getName();
  }

  public @Nullable CoverageRunner getCoverageRunner() {
    return myCoverageRunner;
  }

  public void setCoverageRunner(final @Nullable CoverageRunner coverageRunner) {
    myCoverageRunner = coverageRunner;
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
  @SuppressWarnings("unused")
  public boolean isCoverageEnabled() {
    return myIsCoverageEnabled;
  }

  @SuppressWarnings("unused")
  public void setCoverageEnabled(final boolean isCoverageEnabled) {
    myIsCoverageEnabled = isCoverageEnabled;
  }

  @SuppressWarnings("unused")
  public boolean isBranchCoverageEnabled() {
    return myBranchCoverage;
  }

  @SuppressWarnings("unused")
  public void setBranchCoverage(final boolean branchCoverage) {
    myBranchCoverage = branchCoverage;
  }

  @SuppressWarnings("unused")
  public boolean isTrackPerTestCoverage() {
    return myTrackPerTestCoverage;
  }

  @SuppressWarnings("unused")
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


  public void coverageRunnerExtensionRemoved(@NotNull CoverageRunner runner) {
    if (runner.getId().equals(myCoverageRunner.getId())) {
      myConfiguration.putCopyableUserData(COVERAGE_KEY, null);
      myCoverageRunner = null;
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
      myCoverageRunner = null;
      for (CoverageRunner coverageRunner : CoverageRunner.EP_NAME.getExtensionList()) {
        if (Comparing.strEqual(coverageRunner.getId(), runnerId)) {
          myCoverageRunner = coverageRunner;
          break;
        }
      }
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
    if (myCoverageRunner != null) {
      element.setAttribute(COVERAGE_RUNNER, myCoverageRunner.getId());
    }
  }

  @NonNls
  protected @Nullable String createCoverageFile() {
    if (myCoverageRunner == null) {
      return null;
    }

    Path coverageRootPath = Path.of(PathManager.getSystemPath(), "coverage");
    coverageRootPath.toFile().mkdirs();

    String projectName = FileUtil.sanitizeFileName(myConfiguration.getProject().getName());
    String configName = FileUtil.sanitizeFileName(myConfiguration.getName());
    String separator = coverageFileNameSeparator();
    String extension = myCoverageRunner.getDataFileExtension();
    String path = String.format("%s%s%s.%s", projectName, separator, configName, extension);

    return coverageRootPath.resolve(path).toString();
  }

  protected String coverageFileNameSeparator() {
    return "$";
  }

  public static boolean isApplicableTo(final @NotNull RunConfigurationBase<?> runConfiguration) {
    return getOrNull(runConfiguration) != null || getSuitableEngine(runConfiguration) != null;
  }

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
