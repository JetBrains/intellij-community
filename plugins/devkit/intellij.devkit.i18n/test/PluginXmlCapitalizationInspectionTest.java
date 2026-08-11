// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

@TestDataPath("$CONTENT_ROOT/../testData/inspections/pluginXmlCapitalization/")
public class PluginXmlCapitalizationInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitI18nTestUtil.TESTDATA_PATH + "inspections/pluginXmlCapitalization";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new PluginXmlCapitalizationInspection());
  }

  public void testPluginNameDomElementFix() {
    myFixture.testHighlighting("pluginXmlPluginNameDomElementFix.xml");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");
    myFixture.checkPreviewAndLaunchAction(capitalizeIntention);
    myFixture.checkResultByFile("pluginXmlPluginNameDomElementFix_after.xml");
  }

  public void testSeparatorTextDomElementFix() {
    myFixture.testHighlighting("pluginXmlSeparatorTextDomElementFix.xml");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");
    myFixture.checkPreviewAndLaunchAction(capitalizeIntention);
    myFixture.checkResultByFile("pluginXmlSeparatorTextDomElementFix_after.xml");
  }

  public void testActionDescriptionPropertyFix() {
    myFixture.testHighlighting("pluginXmlActionDescriptionPropertyFix.xml",
                               "ActionDescriptionFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize 'lower case description'");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionWrongCasing.description=Lower case description", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionDescriptionFixBundle.properties",
                                "ActionDescriptionFixBundle_after.properties", true);
  }

  public void testActionTextUnicodeEscapesPropertyFix() {
    myFixture.testHighlighting("pluginXmlActionTextUnicodeEscapesPropertyFix.xml",
                               "ActionTextUnicodeEscapesFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionUnicodeEscapes.text=Copy Path/Reference\\u2026", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextUnicodeEscapesFixBundle.properties",
                                "ActionTextUnicodeEscapesFixBundle_after.properties", true);
  }

  public void testActionTextEscapedBackslashPropertyFix() {
    myFixture.configureByFiles("pluginXmlActionTextEscapedBackslashPropertyFix.xml",
                               "ActionTextEscapedBackslashFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionEscapedBackslash.description=Copies the path\\\\u2026", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextEscapedBackslashFixBundle.properties",
                                "ActionTextEscapedBackslashFixBundle_after.properties", true);
  }

  public void testActionTextLineBreakPropertyFix() {
    myFixture.configureByFiles("pluginXmlActionTextLineBreakPropertyFix.xml",
                               "ActionTextLineBreakFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionLineBreak.description=Copies the path\\nand the reference", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextLineBreakFixBundle.properties",
                                "ActionTextLineBreakFixBundle_after.properties", true);
  }

  public void testActionTextLeadingTabPropertyFix() {
    myFixture.configureByFiles("pluginXmlActionTextLeadingTabPropertyFix.xml",
                               "ActionTextLeadingTabFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionLeadingTab.text=\\u0009Copy Path\\u2026", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextLeadingTabFixBundle.properties",
                                "ActionTextLeadingTabFixBundle_after.properties", true);
  }

  public void testActionTextSpaceDelimiterPropertyFix() {
    myFixture.testHighlighting("pluginXmlActionTextSpaceDelimiterPropertyFix.xml",
                               "ActionTextSpaceDelimiterFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionSpaceDelimiter.text \\=Lower Case", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextSpaceDelimiterFixBundle.properties",
                                "ActionTextSpaceDelimiterFixBundle_after.properties", true);
  }

  public void testActionTextLineContinuationPropertyFix() {
    myFixture.testHighlighting("pluginXmlActionTextLineContinuationPropertyFix.xml",
                               "ActionTextLineContinuationFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionLineContinuation.text=Copy Path \\\n  and Reference", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextLineContinuationFixBundle.properties",
                                "ActionTextLineContinuationFixBundle_after.properties", true);
  }

  public void testActionTextXmlEntityPropertyFix() {
    myFixture.configureByFiles("pluginXmlActionTextXmlEntityPropertyFix.xml",
                               "ActionTextXmlEntityFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize 'copy & paste'");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionXmlEntity.text=Copy &amp; Paste", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextXmlEntityFixBundle.properties",
                                "ActionTextXmlEntityFixBundle_after.properties", true);
  }

  public void testActionTextMnemonicPropertyFix() {
    myFixture.testHighlighting("pluginXmlActionTextMnemonicPropertyFix.xml",
                               "ActionTextMnemonicFixBundle.properties");
    IntentionAction capitalizeIntention = myFixture.findSingleIntention("Properly capitalize 'copy path'");

    String customPreviewText = myFixture.getIntentionPreviewText(capitalizeIntention);
    assertEquals("action.BundleActionMnemonic.text=_Copy Path", customPreviewText);

    myFixture.launchAction(capitalizeIntention);
    myFixture.checkResultByFile("ActionTextMnemonicFixBundle.properties",
                                "ActionTextMnemonicFixBundle_after.properties", true);
  }

  public void testActionPluginName() {
    myFixture.testHighlighting("pluginXmlCapitalization_ActionPluginName.xml",
                               "MyBundle.properties", "MyAction.java", "AnotherBundle.properties");
  }

  public void testActionCorePluginId() {
    myFixture.testHighlighting("pluginXmlCapitalization_CorePluginId.xml",
                               "messages/ActionsBundle.properties");
  }

  public void testActionNoPluginIdInIdeaProject() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(getProject(), true);

    try {
      myFixture.testHighlighting("pluginXmlCapitalization_NoPluginIdInIdeaProject.xml",
                                 "messages/ActionsBundle.properties");
    }
    finally {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(getProject(), false);
    }
  }

  public void testExtensionPoint() {
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
