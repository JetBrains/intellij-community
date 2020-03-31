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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/postfixTemplates")
public class PostfixTemplateDescriptionNotFoundInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/postfixTemplates";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(PostfixTemplateDescriptionNotFoundInspection.class);
    myFixture.addClass("package com.intellij.codeInsight.template.postfix.templates; public class PostfixTemplate {}");
  }

  public void testHighlightingForDescription() {
    myFixture.testHighlighting("MyTemplate.java");
  }

  public void testNoHighlighting() {
    myFixture.copyDirectoryToProject("postfixTemplates", "postfixTemplates");
    myFixture.testHighlighting("MyTemplateWithDescription.java");
  }

  public void testHighlightingForBeforeAfter() {
    myFixture.copyDirectoryToProject("postfixTemplates", "postfixTemplates");
    myFixture.testHighlighting("MyTemplateWithoutBeforeAfter.java");
  }

  public void testQuickFix() {
    myFixture.configureByFiles("MyQuickFixTemplate.java");
    IntentionAction item = myFixture.findSingleIntention("Create description file description.html");
    myFixture.launchAction(item);

    VirtualFile path = myFixture.findFileInTempDir("postfixTemplates/MyQuickFixTemplate/description.html");
    assertNotNull(path);
    assertTrue(path.exists());
  }
}
