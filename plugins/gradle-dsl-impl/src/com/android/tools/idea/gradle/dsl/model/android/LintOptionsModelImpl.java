/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.LintOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LintOptionsModelImpl extends GradleDslBlockModel implements LintOptionsModel {
  @NonNls public static final String ABORT_ON_ERROR = "mAbortOnError";
  @NonNls public static final String ABSOLUTE_PATHS = "mAbsolutePaths";
  @NonNls public static final String CHECK = "mCheck";
  @NonNls public static final String CHECK_ALL_WARNINGS = "mCheckAllWarnings";
  @NonNls public static final String CHECK_RELEASE_BUILDS = "mCheckReleaseBuilds";
  @NonNls public static final String DISABLE = "mDisable";
  @NonNls public static final String ENABLE = "mEnable";
  @NonNls public static final String ERROR = "mError";
  @NonNls public static final String EXPLAIN_ISSUES = "mExplainIssues";
  @NonNls public static final String FATAL = "mFatal";
  @NonNls public static final String HTML_OUTPUT = "mHtmlOutput";
  @NonNls public static final String HTML_REPORT = "mHtmlReport";
  @NonNls public static final String IGNORE = "mIgnore";
  @NonNls public static final String IGNORE_WARNINGS = "mIgnoreWarnings";
  @NonNls public static final String LINT_CONFIG = "mLintConfig";
  @NonNls public static final String NO_LINES = "mNoLines";
  @NonNls public static final String QUIET = "mQuiet";
  @NonNls public static final String SHOW_ALL = "mShowAll";
  @NonNls public static final String TEXT_OUTPUT = "mTextOutput";
  @NonNls public static final String TEXT_REPORT = "mTextReport";
  @NonNls public static final String WARNING = "mWarning";
  @NonNls public static final String WARNINGS_AS_ERRORS = "mWarningsAsErrors";
  @NonNls public static final String XML_OUTPUT = "mXmlOutput";
  @NonNls public static final String XML_REPORT = "mXmlReport";

  public LintOptionsModelImpl(@NotNull LintOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel abortOnError() {
    return getModelForProperty(ABORT_ON_ERROR);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel absolutePaths() {
    return getModelForProperty(ABSOLUTE_PATHS);
  }

  @Override
  @Nullable
  public ResolvedPropertyModel check() {
    return getModelForProperty(CHECK);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel checkAllWarnings() {
    return getModelForProperty(CHECK_ALL_WARNINGS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel checkReleaseBuilds() {
    return getModelForProperty(CHECK_RELEASE_BUILDS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel disable() {
    return getModelForProperty(DISABLE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel enable() {
    return getModelForProperty(ENABLE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel error() {
    return getModelForProperty(ERROR);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel explainIssues() {
    return getModelForProperty(EXPLAIN_ISSUES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel fatal() {
    return getModelForProperty(FATAL);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel htmlOutput() {
    return getFileModelForProperty(HTML_OUTPUT);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel htmlReport() {
    return getModelForProperty(HTML_REPORT);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel ignore() {
    return getModelForProperty(IGNORE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel ignoreWarnings() {
    return getModelForProperty(IGNORE_WARNINGS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel lintConfig() {
    return getFileModelForProperty(LINT_CONFIG);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel noLines() {
    return getModelForProperty(NO_LINES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel quiet() {
    return getModelForProperty(QUIET);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel showAll() {
    return getModelForProperty(SHOW_ALL);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel textOutput() {
    return getFileModelForProperty(TEXT_OUTPUT);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel textReport() {
    return getModelForProperty(TEXT_REPORT);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel warning() {
    return getModelForProperty(WARNING);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel warningsAsErrors() {
    return getModelForProperty(WARNINGS_AS_ERRORS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel xmlOutput() {
    return getFileModelForProperty(XML_OUTPUT);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel xmlReport() {
    return getModelForProperty(XML_REPORT);
  }
}
