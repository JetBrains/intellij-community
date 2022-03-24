// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations.coverage;

import com.intellij.coverage.CoverageLogger;
import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.IDEACoverageRunner;
import com.intellij.coverage.JavaCoverageEngine;
import com.intellij.coverage.JavaCoverageRunner;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for java run configurations with enabled code coverage
 * @author ven
 */
public class JavaCoverageEnabledConfiguration extends CoverageEnabledConfiguration {
  private static final Logger LOG = Logger.getInstance(JavaCoverageEnabledConfiguration.class);

  private ClassFilter[] myCoveragePatterns;

  private boolean myIsMergeWithPreviousResults = false;
  private String mySuiteToMergeWith;

  @NonNls private static final String COVERAGE_PATTERN_ELEMENT_NAME = "pattern";
  @NonNls private static final String COVERAGE_MERGE_ATTRIBUTE_NAME = "merge";
  @NonNls private static final String COVERAGE_MERGE_SUITE_ATT_NAME = "merge_suite";


  public JavaCoverageEnabledConfiguration(final RunConfigurationBase configuration,
                                          final JavaCoverageEngine coverageProvider) {
    super(configuration);
    setCoverageRunner(CoverageRunner.getInstance(IDEACoverageRunner.class));
  }

  public void downloadReport(@NotNull TargetEnvironment environment, @NotNull ProgressIndicator indicator) throws IOException {
    String coverageFilePath = getCoverageFilePath();
    if (coverageFilePath != null) {
      Path path = Paths.get(coverageFilePath);
      TargetEnvironmentFunctions.downloadFromTarget(environment, path, indicator);
    }
  }

  @Nullable
  public static JavaCoverageEnabledConfiguration getFrom(@NotNull final RunConfigurationBase configuration) {
    final CoverageEnabledConfiguration coverageEnabledConfiguration = getOrCreate(configuration);
    if (coverageEnabledConfiguration instanceof JavaCoverageEnabledConfiguration) {
      return (JavaCoverageEnabledConfiguration)coverageEnabledConfiguration;
    }
    return null;
  }

  public void appendCoverageArgument(@NotNull RunConfigurationBase configuration, final SimpleJavaParameters javaParameters) {
    final CoverageRunner runner = getCoverageRunner();
    if (runner instanceof JavaCoverageRunner) {
      final String path = getCoverageFilePath();
      assert path != null; // cannot be null here if runner != null

      String sourceMapPath = null;
      if (JavaCoverageEngine.isSourceMapNeeded(configuration)) {
        sourceMapPath = getSourceMapPath(path);
      }

      final JavaCoverageRunner javaCoverageRunner = (JavaCoverageRunner)runner;
      final String[] patterns = getPatterns();
      final String[] excludePatterns = getExcludePatterns();
      final Project project = configuration.getProject();
      CoverageLogger.logStarted(javaCoverageRunner, isSampling(), isTrackPerTestCoverage(),
                                patterns == null ? 0 : patterns.length,
                                excludePatterns == null ? 0 : excludePatterns.length);
      javaCoverageRunner.appendCoverageArgument(new File(path).getAbsolutePath(),
                                                patterns,
                                                excludePatterns,
                                                javaParameters,
                                                isTrackPerTestCoverage() && !isSampling(),
                                                isSampling(),
                                                sourceMapPath,
                                                project);
    }
  }

  public static String getSourceMapPath(String coverageDataFilePath) {
    return coverageDataFilePath + ".sourceMap";
  }

  public ClassFilter @Nullable [] getCoveragePatterns() {
    return myCoveragePatterns;
  }

  public String @Nullable [] getPatterns() {
    if (myCoveragePatterns != null) {
      List<String> patterns = new ArrayList<>();
      for (ClassFilter coveragePattern : myCoveragePatterns) {
        if (coveragePattern.isEnabled() && coveragePattern.isInclude()) patterns.add(coveragePattern.getPattern());
      }
      return ArrayUtilRt.toStringArray(patterns);
    }
    return null;
  }

  public String @Nullable [] getExcludePatterns() {
    if (myCoveragePatterns != null) {
      List<String> patterns = new ArrayList<>();
      for (ClassFilter coveragePattern : myCoveragePatterns) {
        if (coveragePattern.isEnabled() && !coveragePattern.isInclude()) patterns.add(coveragePattern.getPattern());
      }
      return ArrayUtilRt.toStringArray(patterns);
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
    myIsMergeWithPreviousResults = Boolean.parseBoolean(element.getAttributeValue(COVERAGE_MERGE_ATTRIBUTE_NAME));

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
    super.writeExternal(element);

    // merge with prev
    if (myIsMergeWithPreviousResults) {
      element.setAttribute(COVERAGE_MERGE_ATTRIBUTE_NAME, String.valueOf(true));
    }

    if (myIsMergeWithPreviousResults && mySuiteToMergeWith != null) {
      element.setAttribute(COVERAGE_MERGE_SUITE_ATT_NAME, mySuiteToMergeWith);
    }

    // runner
    final CoverageRunner coverageRunner = getCoverageRunner();
    if (coverageRunner != null) {
      IDEACoverageRunner ideaRunner = CoverageRunner.EP_NAME.findExtension(IDEACoverageRunner.class);
      if (ideaRunner != null && coverageRunner.getId().equals(ideaRunner.getId())) {
        element.removeAttribute(COVERAGE_RUNNER);
      }
    }

    // patterns
    if (myCoveragePatterns != null) {
      for (ClassFilter pattern : myCoveragePatterns) {
        @NonNls final Element patternElement = new Element(COVERAGE_PATTERN_ELEMENT_NAME);
        pattern.writeExternal(patternElement);
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
