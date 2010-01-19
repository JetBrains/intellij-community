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

import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for java run configurations with enabled code coverage
 * @author ven
 */
public class JavaCoverageEnabledConfiguration extends CoverageEnabledConfiguration {
  private static final Logger LOG = Logger.getInstance("com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration");

  private ClassFilter[] myCoveragePatterns;

  private boolean myIsMergeWithPreviousResults = isMergeDataByDefault();
  private String mySuiteToMergeWith;

  @NonNls private static final String COVERAGE_PATTERN_ELEMENT_NAME = "pattern";
  @NonNls private static final String COVERAGE_MERGE_ATTRIBUTE_NAME = "merge";

  public JavaCoverageEnabledConfiguration(ModuleBasedConfiguration configuration) {
    super(configuration);
  }

  @Nullable
  public static JavaCoverageEnabledConfiguration getFrom(final ModuleBasedConfiguration configuration) {
    final CoverageEnabledConfiguration coverageEnabledConfiguration = get(configuration);
    if (coverageEnabledConfiguration instanceof JavaCoverageEnabledConfiguration) {
      return (JavaCoverageEnabledConfiguration)coverageEnabledConfiguration;
    }
    return null;
  }

  /**
   * @return true if coverage data from different runs should be merged by default.
   *         This method returns, for example, true for application configurations and false for unit tests
   */

  protected boolean isMergeDataByDefault() {
    return getConfiguration() instanceof ApplicationConfiguration;
  }

  public boolean canHavePerTestCoverage() {
    return !(getConfiguration() instanceof ApplicationConfiguration);
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

  public void setCoveragePatterns(final ClassFilter[] coveragePatterns) {
    myCoveragePatterns = coveragePatterns;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    // merge with prev results
    final String mergeAttribute = element.getAttributeValue(COVERAGE_MERGE_ATTRIBUTE_NAME);
    myIsMergeWithPreviousResults = mergeAttribute != null && Boolean.valueOf(mergeAttribute).booleanValue();

    // coverage patters
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
  }

  public void writeExternal(Element element) throws WriteExternalException {
    // merge with prev
    element.setAttribute(COVERAGE_MERGE_ATTRIBUTE_NAME, String.valueOf(myIsMergeWithPreviousResults));

    // patterns
    if (myCoveragePatterns != null) {
      for (ClassFilter pattern : myCoveragePatterns) {
        @NonNls final Element patternElement = new Element(COVERAGE_PATTERN_ELEMENT_NAME);
        pattern.writeExternal(patternElement);
        element.addContent(patternElement);
      }
    }
  }

  @Nullable
  public String getCoverageFilePath() {
    if (myCoverageFilePath != null && isMergeWithPreviousResults()) {
      return myCoverageFilePath;
    }
    myCoverageFilePath = createCoverageFile();
    return myCoverageFilePath;
  }

  public boolean isMergeWithPreviousResults() {
    return myIsMergeWithPreviousResults;
  }

  public void setMergeWithPreviousResults(final boolean isMergeWithPreviousResults) {
    myIsMergeWithPreviousResults = isMergeWithPreviousResults;
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
}
