// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.util.PsiUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/pluginXmlCapitalization/")
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

  public void testActionPluginName() {
    myFixture.testHighlighting("pluginXmlCapitalization_ActionPluginName.xml",
                               "MyBundle.properties", "MyAction.java", "AnotherBundle.properties");
  }

  public void testActionCorePluginId() {
    myFixture.testHighlighting("pluginXmlCapitalization_CorePluginId.xml",
                               "messages/ActionsBundle.properties");
  }

  public void testActionNoPluginIdInIdeaProject() {
    PsiUtil.markAsIdeaProject(getProject(), true);

    try {
      myFixture.testHighlighting("pluginXmlCapitalization_NoPluginIdInIdeaProject.xml",
                                 "messages/ActionsBundle.properties");
    }
    finally {
      PsiUtil.markAsIdeaProject(getProject(), false);
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
    myFixture.enableInspections(new SpellCheckingInspection());
    myFixture.testHighlighting("pluginXmlCapitalization_extensionPoint.xml", "MyExtensionPoint.java");
  }
}
