/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.configurations.coverage;

import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.IDEACoverageRunner;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for run configurations with enabled code coverage
 * @author ven
 */
public class CoverageEnabledConfiguration implements JDOMExternalizable{
  public static final Key<CoverageEnabledConfiguration> COVERAGE_KEY = Key.create("com.intellij.coverage");
  
  private static final Logger LOG = Logger.getInstance("com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration");
  public static final Icon WITH_COVERAGE_CONFIGURATION = IconLoader.getIcon("/runConfigurations/withCoverageLayer.png");

  private boolean myIsCoverageEnabled = false;
  private ClassFilter[] myCoveragePatterns;

  private boolean myIsMergeWithPreviousResults = isMergeDataByDefault();
  private String mySuiteToMergeWith;
  private boolean myTrackPerTestCoverage = true;

  @NonNls private static final String COVERAGE_PATTERN_ELEMENT_NAME = "pattern";
  @NonNls private static final String COVERAGE_ENABLED_ATTRIBUTE_NAME = "enabled";
  @NonNls private static final String COVERAGE_MERGE_ATTRIBUTE_NAME = "merge";
  @NonNls private static final String COVERAGE_RUNNER = "runner";
  @NonNls private static final String TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME = "per_test_coverage_enabled";
  @NonNls private static final String SAMPLING_COVERAGE_ATTRIBUTE_NAME = "sample_coverage";
  @NonNls private static final String TRACK_TEST_FOLDERS = "track_test_folders";

  @NonNls private String myCoverageFilePath;
  private CoverageRunner myCoverageRunner;
  private boolean mySampling = false;
  private boolean myTrackTestFolders = false;
  private final String myName;
  private final Project myProject;
  private CoverageSuite myCurrentCoverageSuite;
  private final ModuleBasedConfiguration myConfiguration;
  private String myRunnerId;

  public CoverageEnabledConfiguration(ModuleBasedConfiguration configuration) {
    myConfiguration = configuration;
    myName = configuration.getName();
    myProject = configuration.getProject();
  }

  /**
   * @return true if coverage data from different runs should be merged by default.
   *         This method returns, for example, true for application configurations and false for unit tests
   */

  protected boolean isMergeDataByDefault() {
    return myConfiguration instanceof ApplicationConfiguration;
  }

  public boolean canHavePerTestCoverage() {
    return !(myConfiguration instanceof ApplicationConfiguration);
  }

  public void readExternal(Element element) throws InvalidDataException {
    if (element.getAttributeValue(COVERAGE_ENABLED_ATTRIBUTE_NAME) == null) { //try old format
      Element parentElement = element.getParentElement();
      if (parentElement != null && parentElement.getAttributeValue(COVERAGE_ENABLED_ATTRIBUTE_NAME) != null && parentElement.getAttributeValue(COVERAGE_MERGE_ATTRIBUTE_NAME) != null) {
        element = parentElement;
      }
    }
    myIsCoverageEnabled = element.getAttributeValue(COVERAGE_ENABLED_ATTRIBUTE_NAME) != null && Boolean.valueOf(element.getAttributeValue(COVERAGE_ENABLED_ATTRIBUTE_NAME)).booleanValue();

    final String mergeAttribute = element.getAttributeValue(COVERAGE_MERGE_ATTRIBUTE_NAME);
    myIsMergeWithPreviousResults = mergeAttribute != null && Boolean.valueOf(mergeAttribute).booleanValue();

    final String collectLineInfoAttribute = element.getAttributeValue(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME);
    myTrackPerTestCoverage = collectLineInfoAttribute == null || Boolean.valueOf(collectLineInfoAttribute).booleanValue();

    final String sampling = element.getAttributeValue(SAMPLING_COVERAGE_ATTRIBUTE_NAME);
    mySampling = sampling != null && Boolean.valueOf(sampling).booleanValue();

    final String trackTestFolders = element.getAttributeValue(TRACK_TEST_FOLDERS);
    myTrackTestFolders = trackTestFolders != null && Boolean.valueOf(trackTestFolders).booleanValue();


    final List children = element.getChildren(COVERAGE_PATTERN_ELEMENT_NAME);
    if (children.size() > 0) {
      myCoveragePatterns = new ClassFilter[children.size()];
      for (int i = 0; i < children.size(); i++) {
        myCoveragePatterns[i] = new ClassFilter();
        @NonNls final Element e = (Element)children.get(i);
        myCoveragePatterns[i].readExternal(e);
        final String val = e.getAttributeValue("value");
        if (val != null) {
          myCoveragePatterns[i].setPattern(val);
        }
      }
    }
    myRunnerId = element.getAttributeValue(COVERAGE_RUNNER);
    myCoverageRunner = null;
    for (CoverageRunner coverageRunner : Extensions.getExtensions(CoverageRunner.EP_NAME)) {
      if (Comparing.strEqual(coverageRunner.getId(), myRunnerId)) {
        myCoverageRunner = coverageRunner;
        break;
      }
    }
  }

  public boolean isCoverageEnabled() {
    return myIsCoverageEnabled;
  }

  public void setCoverageEnabled(final boolean isCoverageEnabled) {
    myIsCoverageEnabled = isCoverageEnabled;
  }

  public ClassFilter[] getCoveragePatterns() {
    return myCoveragePatterns;
  }

  public String [] getPatterns() {
    if (myCoveragePatterns != null) {
      List<String> patterns = new ArrayList<String>();
      for (ClassFilter coveragePattern : myCoveragePatterns) {
        if (coveragePattern.isEnabled()) patterns.add(coveragePattern.getPattern());
      }
      return ArrayUtil.toStringArray(patterns);
    }
    return null;
  }

  public CoverageRunner getCoverageRunner() {
    return myCoverageRunner;
  }

  public void setCoverageRunner(final CoverageRunner coverageRunner) {
    myCoverageRunner = coverageRunner;
  }

  public void setCoveragePatterns(final ClassFilter[] coveragePatterns) {
    myCoveragePatterns = coveragePatterns;
  }

  public boolean isTrackPerTestCoverage() {
    return myTrackPerTestCoverage;
  }

  public void setTrackPerTestCoverage(final boolean collectLineInfo) {
    myTrackPerTestCoverage = collectLineInfo;
  }

  public boolean isSampling() {
    return mySampling;
  }

  public void setSampling(final boolean sampling) {
    mySampling = sampling;
  }



  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(COVERAGE_ENABLED_ATTRIBUTE_NAME, String.valueOf(myIsCoverageEnabled));
    element.setAttribute(COVERAGE_MERGE_ATTRIBUTE_NAME, String.valueOf(myIsMergeWithPreviousResults));
    if (!myTrackPerTestCoverage) element.setAttribute(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME, String.valueOf(myTrackPerTestCoverage));
    if (mySampling) element.setAttribute(SAMPLING_COVERAGE_ATTRIBUTE_NAME, String.valueOf(mySampling));
    if (myTrackTestFolders) element.setAttribute(TRACK_TEST_FOLDERS, String.valueOf(myTrackTestFolders));
    if (myCoverageRunner != null) {
      element.setAttribute(COVERAGE_RUNNER, myCoverageRunner.getId());
    } else if (myRunnerId != null) {
      element.setAttribute(COVERAGE_RUNNER, myRunnerId);
    }
    if (myCoveragePatterns != null) {
      for (ClassFilter pattern : myCoveragePatterns) {
        @NonNls final Element patternElement = new Element(COVERAGE_PATTERN_ELEMENT_NAME);
        pattern.writeExternal(patternElement);
        element.addContent(patternElement);
      }
    }
  }

  public void appendCoverageArgument(JavaParameters javaParameters) {
    try {
      if (myCoverageRunner != null) {
        myCoverageRunner.appendCoverageArgument(new File(getCoverageFilePath()).getCanonicalPath(), getPatterns(), javaParameters, myTrackPerTestCoverage,
                                                mySampling);
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @NonNls
  public String getCoverageFilePath() {
    if (myCoverageFilePath == null || !isMergeWithPreviousResults()) {
      @NonNls final String coverageRootPath = PathManager.getSystemPath() + File.separator + "coverage";
      myCoverageFilePath =
          coverageRootPath + File.separator + myProject.getName() + '$' + FileUtil.sanitizeFileName(myName) + "." +
          (myCoverageRunner != null ? myCoverageRunner.getDataFileExtension() : CoverageRunner.getInstance(IDEACoverageRunner.class).getDataFileExtension());
      new File(coverageRootPath).mkdirs();
    }
    return myCoverageFilePath;
  }

  public boolean isMergeWithPreviousResults() {
    return myIsMergeWithPreviousResults;
  }

  public void setMergeWithPreviousResults(final boolean isMergeWithPreviousResults) {
    myIsMergeWithPreviousResults = isMergeWithPreviousResults;
  }

  public void setCoverageFilePath(final String coverageFilePath) {
     myCoverageFilePath = coverageFilePath;
  }

  public boolean isTrackTestFolders() {
    return myTrackTestFolders;
  }

  public void setTrackTestFolders(boolean trackTestFolders) {
    myTrackTestFolders = trackTestFolders;
  }

  @Nullable
  public String getSuiteToMergeWith() {
    if (myIsMergeWithPreviousResults) {
      return mySuiteToMergeWith;
    }
    return null;
  }

  public void setSuiteToMergeWith(String suiteToMegeWith) {
    mySuiteToMergeWith = suiteToMegeWith;
  }

  public CoverageSuite getCurrentCoverageSuite() {
    return myCurrentCoverageSuite;
  }

  public void setCurrentCoverageSuite(CoverageSuite currentCoverageSuite) {
    myCurrentCoverageSuite = currentCoverageSuite;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public static CoverageEnabledConfiguration get(ModuleBasedConfiguration runConfiguration) {
    CoverageEnabledConfiguration configuration = runConfiguration.getCopyableUserData(COVERAGE_KEY);
    if (configuration == null) {
      configuration = new CoverageEnabledConfiguration(runConfiguration);
      runConfiguration.putCopyableUserData(COVERAGE_KEY, configuration);
    }
    return configuration;
  }

   public void setUpCoverageFilters(String className, String packageName) {
    if (getCoveragePatterns() == null) {
      String pattern = null;
      if (className != null && className.length() > 0) {
        int index = className.lastIndexOf('.');
        if (index >= 0) {
          pattern = className.substring(0, index);
        }
      }
      else if (packageName != null) {
        pattern = packageName;
      }


      if (pattern != null && pattern.length() > 0) {
        setCoveragePatterns(new ClassFilter[]{new ClassFilter(pattern + ".*")});
      }
    }
  }

  public String getRunnerId() {
    return myRunnerId;
  }
}
