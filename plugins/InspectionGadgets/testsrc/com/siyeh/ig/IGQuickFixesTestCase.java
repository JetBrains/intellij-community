/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ig;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author anna
 * @since 16-Jun-2009
 */
@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public abstract class IGQuickFixesTestCase extends JavaCodeInsightFixtureTestCase {
  protected String myDefaultHint = null;
  protected String myRelativePath = null;

  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test/com/siyeh/igfixes/";
  }

  protected void doTest() {
    assertNotNull(myDefaultHint);
    final String testName = getTestName(false);
    doTest(testName, myDefaultHint);
  }

  protected void doTest(String hint) {
    final String testName = getTestName(false);
    doTest(testName, hint);
  }

  protected void doTest(final String testName, final String hint) {
    myFixture.configureByFile(getRelativePath() + "/" + testName + ".java");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile(getRelativePath() + "/" + testName + ".after.java");
  }

  protected String getRelativePath() {
    assertNotNull(myRelativePath);
    return myRelativePath;
  }
}
