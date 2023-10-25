// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations.coverage;

import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.CoverageSuite;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

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

  private final Project myProject;
  private final RunConfigurationBase<?> myConfiguration;

  private boolean myIsCoverageEnabled = false;
  private String myRunnerId;
  private CoverageRunner myCoverageRunner;
  private boolean myTrackPerTestCoverage = true;
  private boolean myBranchCoverage = false;
  private boolean myTrackTestFolders = false;

  protected @NonNls String myCoverageFilePath;
  private CoverageSuite myCurrentCoverageSuite;

  public CoverageEnabledConfiguration(@NotNull RunConfigurationBase<?> configuration) {
    myConfiguration = configuration;
    myProject = configuration.getProject();
  }

  public @NotNull RunConfigurationBase<?> getConfiguration() {
    return myConfiguration;
  }

  public boolean isCoverageEnabled() {
    return myIsCoverageEnabled;
  }

  public void setCoverageEnabled(final boolean isCoverageEnabled) {
    myIsCoverageEnabled = isCoverageEnabled;
  }

  public boolean isBranchCoverageEnabled() {
    return myBranchCoverage;
  }

  public void setBranchCoverage(final boolean branchCoverage) {
    myBranchCoverage = branchCoverage;
  }

  public String getRunnerId() {
    return myRunnerId;
  }

  public @Nullable CoverageRunner getCoverageRunner() {
    return myCoverageRunner;
  }

  public void setCoverageRunner(final @Nullable CoverageRunner coverageRunner) {
    myCoverageRunner = coverageRunner;
    myRunnerId = coverageRunner != null ? coverageRunner.getId() : null;
    myCoverageFilePath = null;
  }

  public void coverageRunnerExtensionRemoved(@NotNull CoverageRunner runner) {
    if (runner.getId().equals(myRunnerId)) {
      myCoverageRunner = null;
      myCoverageFilePath = null;
    }
  }

  public boolean isTrackPerTestCoverage() {
    return myTrackPerTestCoverage;
  }

  public void setTrackPerTestCoverage(final boolean testTracking) {
    myTrackPerTestCoverage = testTracking;
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

  public String getName() {
    return myConfiguration.getName();
  }

  public boolean canHavePerTestCoverage() {
    for (CoverageEngine engine : CoverageEngine.EP_NAME.getExtensions()) {
      if (engine.isApplicableTo(myConfiguration)) {
        return engine.canHavePerTestCoverage(myConfiguration);
      }
    }
    return false;
  }


  public static boolean isApplicableTo(final @NotNull RunConfigurationBase runConfiguration) {
    final CoverageEnabledConfiguration configuration = runConfiguration.getCopyableUserData(COVERAGE_KEY);
    if (configuration != null) {
      return true;
    }

    for (CoverageEngine engine : CoverageEngine.EP_NAME.getExtensionList()) {
      if (engine.isApplicableTo(runConfiguration)) {
        return true;
      }
    }

    return false;
  }

  public static @NotNull CoverageEnabledConfiguration getOrCreate(final @NotNull RunConfigurationBase runConfiguration) {
    CoverageEnabledConfiguration configuration = runConfiguration.getCopyableUserData(COVERAGE_KEY);
    if (configuration == null) {
      for (CoverageEngine engine : CoverageEngine.EP_NAME.getExtensionList()) {
        if (engine.isApplicableTo(runConfiguration)) {
          configuration = engine.createCoverageEnabledConfiguration(runConfiguration);
          break;
        }
      }
      LOG.assertTrue(configuration != null,
                     "Coverage enabled run configuration wasn't found for run configuration: " + runConfiguration.getName() +
                     ", type = " + runConfiguration.getClass().getName());
      runConfiguration.putCopyableUserData(COVERAGE_KEY, configuration);
    }
    return configuration;
  }

  public @Nullable @NonNls String getCoverageFilePath() {
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
    myTrackPerTestCoverage = testTrackingAttribute == null || Boolean.valueOf(testTrackingAttribute).booleanValue();

    // line/branch coverage
    myBranchCoverage = !Boolean.parseBoolean(element.getAttributeValue(COVERAGE_TYPE_ATTRIBUTE_NAME, "true"));

    // track test folders
    final String trackTestFolders = element.getAttributeValue(TRACK_TEST_FOLDERS);
    myTrackTestFolders = trackTestFolders != null && Boolean.valueOf(trackTestFolders).booleanValue();

    // coverage runner
    final String runnerId = element.getAttributeValue(COVERAGE_RUNNER);
    if (runnerId != null) {
      myRunnerId = runnerId;
      myCoverageRunner = null;
      for (CoverageRunner coverageRunner : CoverageRunner.EP_NAME.getExtensionList()) {
        if (Comparing.strEqual(coverageRunner.getId(), myRunnerId)) {
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
    if (!myTrackPerTestCoverage) {
      element.setAttribute(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME, String.valueOf(false));
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
    else if (myRunnerId != null) {
      element.setAttribute(COVERAGE_RUNNER, myRunnerId);
    }
  }

  protected @Nullable @NonNls String createCoverageFile() {
    if (myCoverageRunner == null) {
      return null;
    }

    final @NonNls String coverageRootPath = PathManager.getSystemPath() + File.separator + "coverage";
    final String path = coverageRootPath + File.separator + FileUtil.sanitizeFileName(myProject.getName()) + coverageFileNameSeparator()
                        + FileUtil.sanitizeFileName(myConfiguration.getName()) + "." + myCoverageRunner.getDataFileExtension();

    new File(coverageRootPath).mkdirs();
    return path;
  }

  protected String coverageFileNameSeparator() {
    return "$";
  }
}
