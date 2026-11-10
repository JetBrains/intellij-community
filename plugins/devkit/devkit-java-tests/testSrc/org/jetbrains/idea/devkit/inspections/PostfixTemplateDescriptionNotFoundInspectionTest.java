/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.vfs.VirtualFile;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestDataPath("$CONTENT_ROOT/testData/inspections/postfixTemplates")
@TestApplication
public class PostfixTemplateDescriptionNotFoundInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/postfixTemplates");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(PostfixTemplateDescriptionNotFoundInspection.class);
    myFixture.addClass("package com.intellij.codeInsight.template.postfix.templates; public class PostfixTemplate {}");
  }

  @Test
  public void testHighlightingForDescription() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting("MyTemplate.java");
  }

  @Test
  public void testNoHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject("postfixTemplates", "postfixTemplates");
    myFixture.testHighlighting("MyTemplateWithDescription.java");
  }

  @Test
  public void testHighlightingForBeforeAfter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject("postfixTemplates", "postfixTemplates");
    myFixture.testHighlighting("MyTemplateWithoutBeforeAfter.java");
  }

  @Test
  public void testQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("MyQuickFixTemplate.java");
    IntentionAction item = myFixture.findSingleIntention("Create description file description.html");
    myFixture.launchAction(item);

    VirtualFile path = myFixture.findFileInTempDir("postfixTemplates/MyQuickFixTemplate/description.html");
    assertNotNull(path);
    assertTrue(path.exists());
  }
}
