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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.ABORT_ON_ERROR;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.ABSOLUTE_PATHS;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.CHECK;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.CHECK_ALL_WARNINGS;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.CHECK_RELEASE_BUILDS;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.DISABLE;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.ENABLE;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.ERROR;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.EXPLAIN_ISSUES;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.FATAL;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.HTML_OUTPUT;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.HTML_REPORT;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.IGNORE;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.IGNORE_WARNINGS;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.LINT_CONFIG;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.NO_LINES;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.QUIET;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.SHOW_ALL;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.TEXT_OUTPUT;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.TEXT_REPORT;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.WARNING;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.WARNINGS_AS_ERRORS;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.XML_OUTPUT;
import static com.android.tools.idea.gradle.dsl.model.android.LintOptionsModelImpl.XML_REPORT;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public class LintOptionsDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"isAbortOnError", property, ABORT_ON_ERROR, VAR},
    {"isAbsolutePaths", property, ABSOLUTE_PATHS, VAR},
    {"isCheckAllWarnings", property, CHECK_ALL_WARNINGS, VAR},
    // TODO(b/144403889): {"isCheckDependencies", property, CHECK_DEPENDENCIES, VAR},
    // TODO(b/144403889): {"isCheckGeneratedSources", property, CHECK_GENERATED_SOURCES, VAR},
    {"isCheckReleaseBuilds", property, CHECK_RELEASE_BUILDS, VAR},
    // TODO(b/144403889): {"isCheckTestSources", property, CHECK_TEST_SOURCES, VAR},
    {"isExplainIssues", property, EXPLAIN_ISSUES, VAR},
    // TODO(b/144403889): {"isIgnoreTestSources", property, IGNORE_TEST_SOURCES, VAR},
    {"isIgnoreWarnings", property, IGNORE_WARNINGS, VAR},
    {"isNoLines", property, NO_LINES, VAR},
    {"isQuiet", property, QUIET, VAR},
    {"isShowAll", property, SHOW_ALL, VAR},
    {"isWarningsAsErrors", property, WARNINGS_AS_ERRORS, VAR},

    // TODO(b/144403889): {"baselineFile", property, BASELINE, VAR},
    // TODO(b/144403889): {"baseline", exactly(1), BASELINE, SET},
    {"lintConfig", property, LINT_CONFIG, VAR},
    {"htmlOutput", property, HTML_OUTPUT, VAR},
    {"htmlReport", property, HTML_REPORT, VAR},
    {"textOutput", property, TEXT_OUTPUT, VAL},
    {"textOutput", exactly(1), TEXT_OUTPUT, SET}, // special-case String method as well as File
    {"textReport", property, TEXT_REPORT, VAR},
    {"xmlOutput", property, XML_OUTPUT, VAR},
    {"xmlReport", property, XML_REPORT, VAR},

    // There are also exactly(1) variants of these with the same name, but they are redundant for our purposes
    {"check", atLeast(0), CHECK, OTHER},
    {"disable", atLeast(0), DISABLE, OTHER},
    {"enable", atLeast(0), ENABLE, OTHER},
    {"error", atLeast(0), ERROR, OTHER},
    {"fatal", atLeast(0), FATAL, OTHER},
    {"ignore", atLeast(0), IGNORE, OTHER},
    // TODO(b/144403889): {"informational", atLeast(0), INFORMATIONAL, OTHER},
    {"warning", atLeast(0), WARNING, OTHER},
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"abortOnError", property, ABORT_ON_ERROR, VAR},
    {"abortOnError", exactly(1), ABORT_ON_ERROR, SET},
    {"absolutePaths", property, ABSOLUTE_PATHS, VAR},
    {"absolutePaths", exactly(1), ABSOLUTE_PATHS, SET},
    {"checkAllWarnings", property, CHECK_ALL_WARNINGS, VAR},
    {"checkAllWarnings", exactly(1), CHECK_ALL_WARNINGS, SET},
    // TODO(b/144403889): {"checkDependencies", property, CHECK_DEPENDENCIES, VAR},
    // TODO(b/144403889): {"checkDependencies", exactly(1), CHECK_DEPENDENCIES, SET},
    // TODO(b/144403889): {"checkGeneratedSources", property, CHECK_GENERATED_SOURCES, VAR},
    // TODO(b/144403889): {"checkGeneratedSources", exactly(1), CHECK_GENERATED_SOURCES, SET},
    {"checkReleaseBuilds", property, CHECK_RELEASE_BUILDS, VAR},
    {"checkReleaseBuilds", exactly(1), CHECK_RELEASE_BUILDS, SET},
    // TODO(b/144403889): {"checkTestSources", property, CHECK_TEST_SOURCES, VAR},
    // TODO(b/144403889): {"checkTestSources", exactly(1), CHECK_TEST_SOURCES, SET},
    {"explainIssues", property, EXPLAIN_ISSUES, VAR},
    {"explainIssues", exactly(1), EXPLAIN_ISSUES, SET},
    // TODO(b/144403889): {"ignoreTestSources", property, IGNORE_TEST_SOURCES, VAR},
    // TODO(b/144403889): {"ignoreTestSources", exactly(1), IGNORE_TEST_SOURCES, SET},
    {"ignoreWarnings", property, IGNORE_WARNINGS, VAR},
    {"ignoreWarnings", exactly(1), IGNORE_WARNINGS, SET},
    {"noLines", property, NO_LINES, VAR},
    {"noLines", exactly(1), NO_LINES, SET},
    {"quiet", property, QUIET, VAR},
    {"quiet", exactly(1), QUIET, SET},
    {"showAll", property, SHOW_ALL, VAR},
    {"showAll", exactly(1), SHOW_ALL, SET},
    {"warningsAsErrors", property, WARNINGS_AS_ERRORS, VAR},
    {"warningsAsErrors", exactly(1), WARNINGS_AS_ERRORS, SET},

    // TODO(b/144403889): {"baselineFile", property, BASELINE, VAR},
    // TODO(b/144403889): {"baseline", exactly(1), BASELINE, SET},
    {"lintConfig", exactly(1), LINT_CONFIG, SET},
    {"htmlOutput", exactly(1), HTML_OUTPUT, SET},
    {"htmlReport", exactly(1), HTML_REPORT, SET},
    {"textOutput", exactly(1), TEXT_OUTPUT, SET},
    {"textReport", exactly(1), TEXT_REPORT, SET},
    {"xmlOutput", exactly(1), XML_OUTPUT, SET},
    {"xmlReport", exactly(1), XML_REPORT, SET},

    // There are also exactly(1) variants of these with the same name, but they are redundant for our purposes
    {"check", atLeast(0), CHECK, OTHER},
    {"disable", atLeast(0), DISABLE, OTHER},
    {"enable", atLeast(0), ENABLE, OTHER},
    {"error", atLeast(0), ERROR, OTHER},
    {"fatal", atLeast(0), FATAL, OTHER},
    {"ignore", atLeast(0), IGNORE, OTHER},
    // TODO(b/144403889): {"informational", atLeast(0), INFORMATIONAL, OTHER},
    {"warning", atLeast(0), WARNING, OTHER},
  }).collect(toModelMap());
  public static final PropertiesElementDescription<LintOptionsDslElement> LINT_OPTIONS =
    new PropertiesElementDescription<>("lintOptions", LintOptionsDslElement.class, LintOptionsDslElement::new);

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter.isKotlin()) {
      return ktsToModelNameMap;
    }
    else if (converter.isGroovy()) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }


  public LintOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();
    // TODO(xof): implementing ADD_TO_SET method semantics would be a nice win here
    if (property.equals("check")) {
      addToParsedExpressionList(CHECK, element); return;
    }
    else if (property.equals("disable")) {
      addToParsedExpressionList(DISABLE, element); return;
    }
    else if (property.equals("enable")) {
      addToParsedExpressionList(ENABLE, element); return;
    }
    else if (property.equals("error")) {
      addToParsedExpressionList(ERROR, element); return;
    }
    else if (property.equals("fatal")) {
      addToParsedExpressionList(FATAL, element); return;
    }
    else if (property.equals("ignore")) {
      addToParsedExpressionList(IGNORE, element); return;
    }
    // TODO(b/144403889): informational
    else if (property.equals("warning")) {
      addToParsedExpressionList(WARNING, element); return;
    }

    super.addParsedElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    // TODO(b/144403927): the set-properties check, disable, etc. add the elements of the set assigned to the existing set, rather than
    //  assigning a completely new set.  (Also, there might be some other properties like that lurking: check other Set<String> Dsl
    //  properties)
    super.setParsedElement(element);
  }
}
