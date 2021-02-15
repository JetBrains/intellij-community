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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.LINT_OPTIONS_MODEL_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.LINT_OPTIONS_MODEL_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.LINT_OPTIONS_MODEL_EDIT_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.LINT_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.LINT_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.LINT_OPTIONS_MODEL_REMOVE_ONLY_ELEMENTS_IN_THE_LIST;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.LINT_OPTIONS_MODEL_TEXT;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.LintOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Tests for {@link LintOptionsModel}.
 */
public class LintOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(LINT_OPTIONS_MODEL_TEXT);
    verifyLintOptions();
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(LINT_OPTIONS_MODEL_TEXT);
    verifyLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    lintOptions.abortOnError().setValue(false);
    lintOptions.absolutePaths().setValue(true);
    lintOptions.check().getListValue("check-id-2").setValue("check-id-3");
    lintOptions.checkAllWarnings().setValue(false);
    lintOptions.checkReleaseBuilds().setValue(true);
    lintOptions.disable().getListValue("disable-id-2").setValue("disable-id-3");
    lintOptions.enable().getListValue("enable-id-2").setValue("enable-id-3");
    lintOptions.error().getListValue("error-id-1").setValue("error-id-3");
    lintOptions.explainIssues().setValue(false);
    lintOptions.fatal().getListValue("fatal-id-2").setValue("fatal-id-3");
    lintOptions.htmlOutput().setValue("other-html.output");
    lintOptions.htmlReport().setValue(false);
    lintOptions.ignore().getListValue("ignore-id-2").setValue("ignore-id-3");
    lintOptions.ignoreWarnings().setValue(false);
    lintOptions.lintConfig().setValue("other-lint.config");
    lintOptions.noLines().setValue(true);
    lintOptions.quiet().setValue(false);
    lintOptions.showAll().setValue(true);
    lintOptions.textOutput().setValue("other-text.output");
    lintOptions.textReport().setValue(false);
    lintOptions.warning().getListValue("warning-id-2").setValue("warning-id-3");
    lintOptions.warningsAsErrors().setValue(true);
    lintOptions.xmlOutput().setValue("other-xml.output");
    lintOptions.xmlReport().setValue(false);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, LINT_OPTIONS_MODEL_EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    assertEquals("abortOnError", Boolean.FALSE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.TRUE, lintOptions.absolutePaths());
    assertEquals("check", ImmutableList.of("check-id-1", "check-id-3"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.FALSE, lintOptions.checkAllWarnings());
    assertEquals("checkReleaseBuilds", Boolean.TRUE, lintOptions.checkReleaseBuilds());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-3"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-3"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-3", "error-id-2"), lintOptions.error());
    assertEquals("explainIssues", Boolean.FALSE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-3"), lintOptions.fatal());
    assertEquals("htmlOutput", "other-html.output", lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-3"), lintOptions.ignore());
    assertEquals("ignoreWarnings", Boolean.FALSE, lintOptions.ignoreWarnings());
    assertEquals("lintConfig", "other-lint.config", lintOptions.lintConfig());
    assertEquals("noLines", Boolean.TRUE, lintOptions.noLines());
    assertEquals("quiet", Boolean.FALSE, lintOptions.quiet());
    assertEquals("showAll", Boolean.TRUE, lintOptions.showAll());
    assertEquals("textOutput", "other-text.output", lintOptions.textOutput());
    assertEquals("textReport", Boolean.FALSE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-3"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.TRUE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", "other-xml.output", lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.FALSE, lintOptions.xmlReport());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(LINT_OPTIONS_MODEL_ADD_ELEMENTS);
    verifyNullLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    lintOptions.abortOnError().setValue(true);
    lintOptions.absolutePaths().setValue(false);
    lintOptions.check().addListValue().setValue("check-id-1");
    lintOptions.checkAllWarnings().setValue(true);
    lintOptions.checkReleaseBuilds().setValue(false);
    lintOptions.disable().addListValue().setValue("disable-id-1");
    lintOptions.enable().addListValue().setValue("enable-id-1");
    lintOptions.error().addListValue().setValue("error-id-1");
    lintOptions.explainIssues().setValue(true);
    lintOptions.fatal().addListValue().setValue("fatal-id-1");
    lintOptions.htmlOutput().setValue("html.output");
    lintOptions.htmlReport().setValue(false);
    lintOptions.ignore().addListValue().setValue("ignore-id-1");
    lintOptions.ignoreWarnings().setValue(true);
    lintOptions.lintConfig().setValue("lint.config");
    lintOptions.noLines().setValue(false);
    lintOptions.quiet().setValue(true);
    lintOptions.showAll().setValue(false);
    lintOptions.textOutput().setValue("text.output");
    lintOptions.textReport().setValue(true);
    lintOptions.warning().addListValue().setValue("warning-id-1");
    lintOptions.warningsAsErrors().setValue(false);
    lintOptions.xmlOutput().setValue("xml.output");
    lintOptions.xmlReport().setValue(true);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, LINT_OPTIONS_MODEL_ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();

    assertEquals("abortOnError", Boolean.TRUE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.FALSE, lintOptions.absolutePaths());
    assertEquals("check", ImmutableList.of("check-id-1"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.TRUE, lintOptions.checkAllWarnings());
    assertEquals("checkReleaseBuilds", Boolean.FALSE, lintOptions.checkReleaseBuilds());
    assertEquals("disable", ImmutableList.of("disable-id-1"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1"), lintOptions.error());
    assertEquals("explainIssues", Boolean.TRUE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1"), lintOptions.fatal());
    assertEquals("htmlOutput", "html.output", lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1"), lintOptions.ignore());
    assertEquals("ignoreWarnings", Boolean.TRUE, lintOptions.ignoreWarnings());
    assertEquals("lintConfig", "lint.config", lintOptions.lintConfig());
    assertEquals("noLines", Boolean.FALSE, lintOptions.noLines());
    assertEquals("quiet", Boolean.TRUE, lintOptions.quiet());
    assertEquals("showAll", Boolean.FALSE, lintOptions.showAll());
    assertEquals("textOutput", "text.output", lintOptions.textOutput());
    assertEquals("textReport", Boolean.TRUE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.FALSE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", "xml.output", lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.TRUE, lintOptions.xmlReport());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(LINT_OPTIONS_MODEL_TEXT);
    verifyLintOptions();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    checkForValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    lintOptions.abortOnError().delete();
    lintOptions.absolutePaths().delete();
    lintOptions.check().delete();
    lintOptions.checkAllWarnings().delete();
    lintOptions.checkReleaseBuilds().delete();
    lintOptions.disable().delete();
    lintOptions.enable().delete();
    lintOptions.error().delete();
    lintOptions.explainIssues().delete();
    lintOptions.fatal().delete();
    lintOptions.htmlOutput().delete();
    lintOptions.htmlReport().delete();
    lintOptions.ignore().delete();
    lintOptions.ignoreWarnings().delete();
    lintOptions.lintConfig().delete();
    lintOptions.noLines().delete();
    lintOptions.quiet().delete();
    lintOptions.showAll().delete();
    lintOptions.textOutput().delete();
    lintOptions.textReport().delete();
    lintOptions.warning().delete();
    lintOptions.warningsAsErrors().delete();
    lintOptions.xmlOutput().delete();
    lintOptions.xmlReport().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    checkForInValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    verifyNullLintOptions();
  }

  private void verifyLintOptions() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    assertEquals("abortOnError", Boolean.TRUE, lintOptions.abortOnError());
    assertEquals("absolutePaths", Boolean.FALSE, lintOptions.absolutePaths());
    assertEquals("check", ImmutableList.of("check-id-1", "check-id-2"), lintOptions.check());
    assertEquals("checkAllWarnings", Boolean.TRUE, lintOptions.checkAllWarnings());
    assertEquals("checkReleaseBuilds", Boolean.FALSE, lintOptions.checkReleaseBuilds());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-2"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-2"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1", "error-id-2"), lintOptions.error());
    assertEquals("explainIssues", Boolean.TRUE, lintOptions.explainIssues());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-2"), lintOptions.fatal());
    assertEquals("htmlOutput", "html.output", lintOptions.htmlOutput());
    assertEquals("htmlReport", Boolean.FALSE, lintOptions.htmlReport());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-2"), lintOptions.ignore());
    assertEquals("ignoreWarnings", Boolean.TRUE, lintOptions.ignoreWarnings());
    assertEquals("lintConfig", "lint.config", lintOptions.lintConfig());
    assertEquals("noLines", Boolean.FALSE, lintOptions.noLines());
    assertEquals("quiet", Boolean.TRUE, lintOptions.quiet());
    assertEquals("showAll", Boolean.FALSE, lintOptions.showAll());
    assertEquals("textOutput", "text.output", lintOptions.textOutput());
    assertEquals("textReport", Boolean.TRUE, lintOptions.textReport());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-2"), lintOptions.warning());
    assertEquals("warningsAsErrors", Boolean.FALSE, lintOptions.warningsAsErrors());
    assertEquals("xmlOutput", "xml.output", lintOptions.xmlOutput());
    assertEquals("xmlReport", Boolean.TRUE, lintOptions.xmlReport());
  }

  private void verifyNullLintOptions() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    assertMissingProperty("abortOnError", lintOptions.abortOnError());
    assertMissingProperty("absolutePaths", lintOptions.absolutePaths());
    assertMissingProperty("check", lintOptions.check());
    assertMissingProperty("checkAllWarnings", lintOptions.checkAllWarnings());
    assertMissingProperty("checkReleaseBuilds", lintOptions.checkReleaseBuilds());
    assertMissingProperty("disable", lintOptions.disable());
    assertMissingProperty("enable", lintOptions.enable());
    assertMissingProperty("error", lintOptions.error());
    assertMissingProperty("explainIssues", lintOptions.explainIssues());
    assertMissingProperty("fatal", lintOptions.fatal());
    assertMissingProperty("htmlOutput", lintOptions.htmlOutput());
    assertMissingProperty("htmlReport", lintOptions.htmlReport());
    assertMissingProperty("ignore", lintOptions.ignore());
    assertMissingProperty("ignoreWarnings", lintOptions.ignoreWarnings());
    assertMissingProperty("lintConfig", lintOptions.lintConfig());
    assertMissingProperty("noLines", lintOptions.noLines());
    assertMissingProperty("quiet", lintOptions.quiet());
    assertMissingProperty("showAll", lintOptions.showAll());
    assertMissingProperty("textOutput", lintOptions.textOutput());
    assertMissingProperty("textReport", lintOptions.textReport());
    assertMissingProperty("warning", lintOptions.warning());
    assertMissingProperty("warningsAsErrors", lintOptions.warningsAsErrors());
    assertMissingProperty("xmlOutput", lintOptions.xmlOutput());
    assertMissingProperty("xmlReport", lintOptions.xmlReport());
  }

  @Test
  public void testRemoveOneOfElementsInTheList() throws Exception {
    writeToBuildFile(LINT_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    assertEquals("check", ImmutableList.of("check-id-1", "check-id-2"), lintOptions.check());
    assertEquals("disable", ImmutableList.of("disable-id-1", "disable-id-2"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-1", "enable-id-2"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1", "error-id-2"), lintOptions.error());
    assertEquals("fatal", ImmutableList.of("fatal-id-1", "fatal-id-2"), lintOptions.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id-1", "ignore-id-2"), lintOptions.ignore());
    assertEquals("warning", ImmutableList.of("warning-id-1", "warning-id-2"), lintOptions.warning());

    lintOptions.check().getListValue("check-id-1").delete();
    lintOptions.disable().getListValue("disable-id-2").delete();
    lintOptions.enable().getListValue("enable-id-1").delete();
    lintOptions.error().getListValue("error-id-2").delete();
    lintOptions.fatal().getListValue("fatal-id-1").delete();
    lintOptions.ignore().getListValue("ignore-id-2").delete();
    lintOptions.warning().getListValue("warning-id-1").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, LINT_OPTIONS_MODEL_REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    assertEquals("check", ImmutableList.of("check-id-2"), lintOptions.check());
    assertEquals("disable", ImmutableList.of("disable-id-1"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id-2"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id-1"), lintOptions.error());
    assertEquals("fatal", ImmutableList.of("fatal-id-2"), lintOptions.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id-1"), lintOptions.ignore());
    assertEquals("warning", ImmutableList.of("warning-id-2"), lintOptions.warning());
  }

  @Test
  public void testRemoveOnlyElementsInTheList() throws Exception {
    writeToBuildFile(LINT_OPTIONS_MODEL_REMOVE_ONLY_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    LintOptionsModel lintOptions = android.lintOptions();
    checkForValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    assertEquals("check", ImmutableList.of("check-id"), lintOptions.check());
    assertEquals("disable", ImmutableList.of("disable-id"), lintOptions.disable());
    assertEquals("enable", ImmutableList.of("enable-id"), lintOptions.enable());
    assertEquals("error", ImmutableList.of("error-id"), lintOptions.error());
    assertEquals("fatal", ImmutableList.of("fatal-id"), lintOptions.fatal());
    assertEquals("ignore", ImmutableList.of("ignore-id"), lintOptions.ignore());
    assertEquals("warning", ImmutableList.of("warning-id"), lintOptions.warning());

    lintOptions.check().getListValue("check-id").delete();
    lintOptions.disable().getListValue("disable-id").delete();
    lintOptions.enable().getListValue("enable-id").delete();
    lintOptions.error().getListValue("error-id").delete();
    lintOptions.fatal().getListValue("fatal-id").delete();
    lintOptions.ignore().getListValue("ignore-id").delete();
    lintOptions.warning().getListValue("warning-id").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    lintOptions = android.lintOptions();
    checkForInValidPsiElement(lintOptions, LintOptionsModelImpl.class);
    assertMissingProperty("check", lintOptions.check());
    assertMissingProperty("disable", lintOptions.disable());
    assertMissingProperty("enable", lintOptions.enable());
    assertMissingProperty("error", lintOptions.error());
    assertMissingProperty("fatal", lintOptions.fatal());
    assertMissingProperty("ignore", lintOptions.ignore());
    assertMissingProperty("warning", lintOptions.warning());
  }
}
