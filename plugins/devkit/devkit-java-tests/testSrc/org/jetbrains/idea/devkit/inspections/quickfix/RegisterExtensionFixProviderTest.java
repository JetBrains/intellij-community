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
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Dmitry Avdeev
 */
@TestDataPath("$CONTENT_ROOT/testData/inspections/registerExtensionFix")
@TestApplication
public class RegisterExtensionFixProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registerExtensionFix");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
    myFixture.enableInspections(new UnusedDeclarationInspectionBase(true));
  }

  @Test
  public void testCreateLocalInspectionMapping() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.codeInspection; public class LocalInspectionTool {} ");

    myFixture.testHighlighting("LocalInspection.java", "plugin.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register inspection");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("plugin.xml", "localInspection.xml", true);
  }

  @Test
  public void testCreateGlobalInspectionMapping() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.codeInspection; public class GlobalInspectionTool {} ");

    myFixture.testHighlighting("GlobalInspection.java", "plugin.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register inspection");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("plugin.xml", "globalInspection.xml", true);
  }

  @Test
  public void testCreateExtensionPointMapping() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("MyExtensionPoint.java",
                               "extensionPoint.xml",
                               "ExtensionPoint.java");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPoint.xml", "extensionPoint_after.xml", true);
  }

  @Test
  public void testCreateExtensionPointMappingWithKnownRequiredAttribute() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.lang; public class LanguageExtensionPoint {}");

    myFixture.testHighlighting("MyLanguageExtensionPoint.java",
                               "MyLanguageExtensionPointInterface.java",
                               "extensionPointWithKnownRequiredAttribute.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointWithKnownRequiredAttribute.xml",
                                "extensionPointWithKnownRequiredAttribute_after.xml", true);
  }

  @Test
  public void testCreateExtensionPointMappingWithKnownRequiredAttributeAndTag() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.lang; public class LanguageExtensionPoint {}");

    myFixture.testHighlighting("MyLanguageExtensionPoint.java",
                               "MyLanguageExtensionPointInterface.java",
                               "extensionPointWithKnownRequiredAttributeAndTag.xml");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointWithKnownRequiredAttributeAndTag.xml",
                                "extensionPointWithKnownRequiredAttributeAndTag_after.xml", true);
  }

  @Test
  public void testCreateExtensionPointMappingNoExtensionsTag() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("MyExtensionPoint.java",
                               "extensionPointNoExtensionsTag.xml",
                               "ExtensionPoint.java");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointNoExtensionsTag.xml", "extensionPointNoExtensionsTag_after.xml", true);
  }

  @Test
  public void testCreateExtensionPointMappingNoPluginId() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("MyExtensionPoint.java",
                               "extensionPointNoPluginId.xml",
                               "ExtensionPoint.java");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointNoPluginId.xml", "extensionPointNoPluginId_after.xml", true);
  }

  @Test
  public void testCreateExtensionPointQualifiedNameMapping() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("MyExtensionPoint.java",
                               "extensionPointQualifiedName.xml",
                               "ExtensionPoint.java");
    IntentionAction intention = myFixture.findSingleIntention("Register extension");
    myFixture.launchAction(intention);
    myFixture.checkResultByFile("extensionPointQualifiedName.xml", "extensionPointQualifiedName_after.xml", true);
  }
}
