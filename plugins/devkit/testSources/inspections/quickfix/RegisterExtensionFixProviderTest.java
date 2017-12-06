/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 */
@TestDataPath("$CONTENT_ROOT/testData/inspections/registerExtensionFix")
public class RegisterExtensionFixProviderTest extends LightCodeInsightFixtureTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnusedDeclarationInspectionBase(true));
  }

  public void testCreateLocalInspectionMapping() {
    myFixture.addClass("package com.intellij.codeInspection; public class LocalInspectionTool {} ");

    myFixture.testHighlighting("LocalInspection.java", "plugin.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register inspection");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("plugin.xml", "localInspection.xml", true);
  }

  public void testCreateGlobalInspectionMapping() {
    myFixture.addClass("package com.intellij.codeInspection; public class GlobalInspectionTool {} ");

    myFixture.testHighlighting("GlobalInspection.java", "plugin.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register inspection");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("plugin.xml", "globalInspection.xml", true);
  }

  public void testCreateExtensionPointMapping() {
    myFixture.testHighlighting("MyExtensionPoint.java",
                               "extensionPoint.xml",
                               "ExtensionPoint.java");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPoint.xml", "extensionPoint_after.xml", true);
  }

  public void testCreateExtensionPointMappingWithKnownRequiredAttribute() {
    myFixture.addClass("package com.intellij.lang; public class LanguageExtensionPoint {}");

    myFixture.testHighlighting("MyLanguageExtensionPoint.java",
                               "MyLanguageExtensionPointInterface.java",
                               "extensionPointWithKnownRequiredAttribute.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointWithKnownRequiredAttribute.xml",
                                "extensionPointWithKnownRequiredAttribute_after.xml", true);
  }

  public void testCreateExtensionPointMappingWithKnownRequiredAttributeAndTag() {
    myFixture.addClass("package com.intellij.lang; public class LanguageExtensionPoint {}");

    myFixture.testHighlighting("MyLanguageExtensionPoint.java",
                               "MyLanguageExtensionPointInterface.java",
                               "extensionPointWithKnownRequiredAttributeAndTag.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointWithKnownRequiredAttributeAndTag.xml",
                                "extensionPointWithKnownRequiredAttributeAndTag_after.xml", true);
  }

  public void testCreateExtensionPointMappingNoExtensionsTag() {
    myFixture.testHighlighting("MyExtensionPoint.java",
                               "extensionPointNoExtensionsTag.xml",
                               "ExtensionPoint.java");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointNoExtensionsTag.xml", "extensionPointNoExtensionsTag_after.xml", true);
  }

  public void testCreateExtensionPointMappingNoPluginId() {
    myFixture.testHighlighting("MyExtensionPoint.java",
                               "extensionPointNoPluginId.xml",
                               "ExtensionPoint.java");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointNoPluginId.xml", "extensionPointNoPluginId_after.xml", true);
  }

  public void testCreateExtensionPointQualifiedNameMapping() {
    myFixture.testHighlighting("MyExtensionPoint.java",
                               "extensionPointQualifiedName.xml",
                               "ExtensionPoint.java");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointQualifiedName.xml", "extensionPointQualifiedName_after.xml", true);
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/inspections/registerExtensionFix";
  }
}
