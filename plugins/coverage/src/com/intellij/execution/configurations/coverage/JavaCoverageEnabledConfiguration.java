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
import com.intellij.coverage.IDEACoverageRunner;
import com.intellij.coverage.JavaCoverageEngine;
import com.intellij.coverage.JavaCoverageRunner;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for java run configurations with enabled code coverage
 * @author ven
 */
public class JavaCoverageEnabledConfiguration extends CoverageEnabledConfiguration {
  private static final Logger LOG = Logger.getInstance("com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration");

  private ClassFilter[] myCoveragePatterns;

  private boolean myIsMergeWithPreviousResults = false;
  private String mySuiteToMergeWith;

  @NonNls private static final String COVERAGE_PATTERN_ELEMENT_NAME = "pattern";
  @NonNls private static final String COVERAGE_MERGE_ATTRIBUTE_NAME = "merge";
  @NonNls private static final String COVERAGE_MERGE_SUITE_ATT_NAME = "merge_suite";

  private JavaCoverageEngine myCoverageProvider;

  public JavaCoverageEnabledConfiguration(final RunConfigurationBase configuration,
                                          final JavaCoverageEngine coverageProvider) {
    super(configuration);
    myCoverageProvider = coverageProvider;
    setCoverageRunner(CoverageRunner.getInstance(IDEACoverageRunner.class));
  }

  @Nullable
  public static JavaCoverageEnabledConfiguration getFrom(final RunConfigurationBase configuration) {
    final CoverageEnabledConfiguration coverageEnabledConfiguration = getOrCreate(configuration);
    if (coverageEnabledConfiguration instanceof JavaCoverageEnabledConfiguration) {
      return (JavaCoverageEnabledConfiguration)coverageEnabledConfiguration;
    }
    return null;
  }

  public void appendCoverageArgument(RunConfigurationBase configuration, final SimpleJavaParameters javaParameters) {
    final CoverageRunner runner = getCoverageRunner();
    try {
      if (runner != null && runner instanceof JavaCoverageRunner) {
        final String path = getCoverageFilePath();
        assert path != null; // cannot be null here if runner != null

        String sourceMapPath = null;
        if (myCoverageProvider.isSourceMapNeeded(configuration)) {
          sourceMapPath = getSourceMapPath(path);
        }

        ((JavaCoverageRunner)runner).appendCoverageArgument(new File(path).getCanonicalPath(),
                                                            getPatterns(),
                                                            javaParameters,
                                                            isTrackPerTestCoverage() && !isSampling(),
                                                            isSampling(),
                                                            sourceMapPath);
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public static String getSourceMapPath(String coverageDataFilePath) {
    return coverageDataFilePath + ".sourceMap";
  }

  @NotNull
  public JavaCoverageEngine getCoverageProvider() {
    return myCoverageProvider;
  }

  public ClassFilter[] getCoveragePatterns() {
    return myCoveragePatterns;
  }

  @Nullable
  public String [] getPatterns() {
    if (myCoveragePatterns != null) {
      List<String> patterns = new ArrayList<>();
      for (ClassFilter coveragePattern : myCoveragePatterns) {
        if (coveragePattern.isEnabled()) patterns.add(coveragePattern.getPattern());
      }
      return ArrayUtil.toStringArray(patterns);
    }
    return null;
  }

  public void setCoveragePatterns(final ClassFilter[] coveragePatterns) {
    myCoveragePatterns = coveragePatterns;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    // merge with prev results
    final String mergeAttribute = element.getAttributeValue(COVERAGE_MERGE_ATTRIBUTE_NAME);
    myIsMergeWithPreviousResults = mergeAttribute != null && Boolean.valueOf(mergeAttribute).booleanValue();

    mySuiteToMergeWith = element.getAttributeValue(COVERAGE_MERGE_SUITE_ATT_NAME);

    // coverage patters
    List<Element> children = element.getChildren(COVERAGE_PATTERN_ELEMENT_NAME);
    if (children.size() > 0) {
      myCoveragePatterns = new ClassFilter[children.size()];
      for (int i = 0; i < children.size(); i++) {
        Element e = children.get(i);
        myCoveragePatterns[i] = createClassFilter(e);
        String val = e.getAttributeValue("value");
        if (val != null) {
          myCoveragePatterns[i].setPattern(val);
        }
      }
    }
  }

  public static ClassFilter createClassFilter(Element element) throws InvalidDataException {
    ClassFilter filter = new ClassFilter();
    DefaultJDOMExternalizer.readExternal(filter, element);
    return filter;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    // just for backward compatibility with settings format before "Huge Coverage Refactoring"
    // see [IDEA-56800] ProjectRunConfigurationManager component: "coverage" extension: "merge" attribute is misplaced
    // here we can't use super.writeExternal(...) due to differences in format between IDEA 10 and IDEA 9.x

    // enabled
    element.setAttribute(COVERAGE_ENABLED_ATTRIBUTE_NAME, String.valueOf(isCoverageEnabled()));

    // merge with prev
    element.setAttribute(COVERAGE_MERGE_ATTRIBUTE_NAME, String.valueOf(myIsMergeWithPreviousResults));

    if (myIsMergeWithPreviousResults && mySuiteToMergeWith != null) {
      element.setAttribute(COVERAGE_MERGE_SUITE_ATT_NAME, mySuiteToMergeWith);
    }

    // track per test
    final boolean trackPerTestCoverage = isTrackPerTestCoverage();
    if (!trackPerTestCoverage) {
      element.setAttribute(TRACK_PER_TEST_COVERAGE_ATTRIBUTE_NAME, String.valueOf(trackPerTestCoverage));
    }

    // sampling
    final boolean sampling = isSampling();
    if (sampling) {
      element.setAttribute(SAMPLING_COVERAGE_ATTRIBUTE_NAME, String.valueOf(sampling));
    }

    // test folders
    final boolean trackTestFolders = isTrackTestFolders();
    if (trackTestFolders) {
      element.setAttribute(TRACK_TEST_FOLDERS, String.valueOf(trackTestFolders));
    }

    // runner
    final CoverageRunner coverageRunner = getCoverageRunner();
    final String runnerId = getRunnerId();
    if (coverageRunner != null) {
      element.setAttribute(COVERAGE_RUNNER, coverageRunner.getId());
    } else if (runnerId != null) {
      element.setAttribute(COVERAGE_RUNNER, runnerId);
    }

    // patterns
    if (myCoveragePatterns != null) {
      for (ClassFilter pattern : myCoveragePatterns) {
        @NonNls final Element patternElement = new Element(COVERAGE_PATTERN_ELEMENT_NAME);
        DefaultJDOMExternalizer.writeExternal(pattern, patternElement);
        element.addContent(patternElement);
      }
    }
  }

  @Override
  @Nullable
  public String getCoverageFilePath() {
    if (myCoverageFilePath != null ) {
      return myCoverageFilePath;
    }
    myCoverageFilePath = createCoverageFile();
    return myCoverageFilePath;
  }

  public void setUpCoverageFilters(@Nullable String className, @Nullable String packageName) {
    if (getCoveragePatterns() == null) {
      String pattern = null;
      if (!StringUtil.isEmpty(className)) {
        int index = className.lastIndexOf('.');
        if (index >= 0) {
          pattern = className.substring(0, index);
        }
      }
      else if (packageName != null) {
        pattern = packageName;
      }

      if (!StringUtil.isEmpty(pattern)) {
        setCoveragePatterns(new ClassFilter[]{new ClassFilter(pattern + ".*")});
      }
    }
  }
}
