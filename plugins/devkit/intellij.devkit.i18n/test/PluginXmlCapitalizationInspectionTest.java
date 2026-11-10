// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestDataPath("$CONTENT_ROOT/../testData/inspections/pluginXmlCapitalization/")
@TestApplication
public class PluginXmlCapitalizationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitI18nTestUtil.TESTDATA_PATH + "inspections/pluginXmlCapitalization");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new PluginXmlCapitalizationInspection());
  }

  @Test
  public void testPluginNameDomElementFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlPluginNameDomElementFix.xml");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(capitalizeIntention);
      myFixture.checkResultByFile("pluginXmlPluginNameDomElementFix_after.xml");
    });
  }

  @Test
  public void testSeparatorTextDomElementFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlSeparatorTextDomElementFix.xml");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(capitalizeIntention);
      myFixture.checkResultByFile("pluginXmlSeparatorTextDomElementFix_after.xml");
    });
  }

  @Test
  public void testActionDescriptionPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlActionDescriptionPropertyFix.xml",
                               "ActionDescriptionFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize 'lower case description'");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionWrongCasing.description=Lower case description", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionDescriptionFixBundle.properties",
                                "ActionDescriptionFixBundle_after.properties", true);
  }

  @Test
  public void testActionTextUnicodeEscapesPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlActionTextUnicodeEscapesPropertyFix.xml",
                               "ActionTextUnicodeEscapesFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionUnicodeEscapes.text=Copy Path/Reference\\u2026", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextUnicodeEscapesFixBundle.properties",
                                "ActionTextUnicodeEscapesFixBundle_after.properties", true);
  }

  @Test
  public void testActionTextEscapedBackslashPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("pluginXmlActionTextEscapedBackslashPropertyFix.xml",
                               "ActionTextEscapedBackslashFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionEscapedBackslash.description=Copies the path\\\\u2026", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextEscapedBackslashFixBundle.properties",
                                "ActionTextEscapedBackslashFixBundle_after.properties", true);
  }

  @Test
  public void testActionTextLineBreakPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("pluginXmlActionTextLineBreakPropertyFix.xml",
                               "ActionTextLineBreakFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionLineBreak.description=Copies the path\\nand the reference", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextLineBreakFixBundle.properties",
                                "ActionTextLineBreakFixBundle_after.properties", true);
  }

  @Test
  public void testActionTextLeadingTabPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("pluginXmlActionTextLeadingTabPropertyFix.xml",
                               "ActionTextLeadingTabFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionLeadingTab.text=\\u0009Copy Path\\u2026", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextLeadingTabFixBundle.properties",
                                "ActionTextLeadingTabFixBundle_after.properties", true);
  }

  @Test
  public void testActionTextSpaceDelimiterPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlActionTextSpaceDelimiterPropertyFix.xml",
                               "ActionTextSpaceDelimiterFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionSpaceDelimiter.text \\=Lower Case", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextSpaceDelimiterFixBundle.properties",
                                "ActionTextSpaceDelimiterFixBundle_after.properties", true);
  }

  @Test
  public void testActionTextLineContinuationPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlActionTextLineContinuationPropertyFix.xml",
                               "ActionTextLineContinuationFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionLineContinuation.text=Copy Path \\\n  and Reference", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextLineContinuationFixBundle.properties",
                                "ActionTextLineContinuationFixBundle_after.properties", true);
  }

  @Test
  public void testActionTextXmlEntityPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("pluginXmlActionTextXmlEntityPropertyFix.xml",
                               "ActionTextXmlEntityFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize 'copy & paste'");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionXmlEntity.text=Copy &amp; Paste", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextXmlEntityFixBundle.properties",
                                "ActionTextXmlEntityFixBundle_after.properties", true);
  }

  @Test
  public void testActionTextMnemonicPropertyFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlActionTextMnemonicPropertyFix.xml",
                               "ActionTextMnemonicFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize 'copy path'");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionMnemonic.text=_Copy Path", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextMnemonicFixBundle.properties",
                                "ActionTextMnemonicFixBundle_after.properties", true);
  }

  @Test
  public void testActionPluginName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlCapitalization_ActionPluginName.xml",
                               "MyBundle.properties", "MyAction.java", "AnotherBundle.properties");
  }

  @Test
  public void testActionCorePluginId() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("pluginXmlCapitalization_CorePluginId.xml",
                               "messages/ActionsBundle.properties");
  }

  @Test
  public void testActionNoPluginIdInIdeaProject() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IntelliJProjectUtil.markAsIntelliJPlatformProject(myFixture.getProject(), true);

    try {
      myFixture.testHighlighting("pluginXmlCapitalization_NoPluginIdInIdeaProject.xml",
                                 "messages/ActionsBundle.properties");
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(myFixture.getProject(), false);
    }
  }

  @Test
  public void testExtensionPoint() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Attribute { String value() default \"\";}");
    myFixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Tag { String value() default \"\";}");

    myFixture.addClass("package org.jetbrains.annotations; public @interface NonNls {}");
    myFixture.addClass("package org.jetbrains.annotations; public @interface Nls {" +
                       "  enum Capitalization {NotSpecified,Title,Sentence}" +
                       "  Capitalization capitalization() default Capitalization.NotSpecified;" +
                       "}");
    myFixture.enableInspections(new GrazieSpellCheckingInspection());
    myFixture.testHighlighting("pluginXmlCapitalization_extensionPoint.xml", "MyExtensionPoint.java");
  }
}
