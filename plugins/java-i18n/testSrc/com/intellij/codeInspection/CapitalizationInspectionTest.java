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
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.capitalization.AnnotateCapitalizationIntention;
import com.intellij.codeInspection.capitalization.TitleCapitalizationInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class CapitalizationInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testTitleCapitalization() {
    doTest(true);
  }

  public void testSentenceCapitalization() {
    doTest(true);
  }

  public void testTernaryAndParentheses() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testMultipleReturns() {
    doTest(true);
  }

  public void testEmptySentence() {
    doTest(false);
  }

  public void testArgument() {
    doTest(false);
  }

  public void testConstructorArgument() {
    doTest(false);
  }

  public void testSuperConstructorArgument() {
    doTest(false);
  }

  public void testPropertyTest() {
    String props = "property.lowercase=hello world\n" +
                   "property.titlecase=Hello World\n" +
                   "property.parameterized=Hello {0}\n" +
                   "property.choice.title=Hello {0,choice,0#World|1#Universe}\n" +
                   "property.choice.mixed=Hello {0,choice,0#World|1#universe}\n" +
                   "property.choice.lower=Hello {0,choice,0#world|1#universe}\n" + 
                   "property.sentence.with.quote='return' is not allowed here";
    myFixture.addFileToProject("MyBundle.properties", props);
    doTest(false);
  }

  public void testRecursiveMethod() {
    myFixture.testHighlighting(getTestName(false) + ".java");
    assertEmpty(myFixture.filterAvailableIntentions("Properly capitalize"));
  }

  public void testIntention() {
    myFixture.configureByFile("Intention.java");
    AnnotateCapitalizationIntention intention = new AnnotateCapitalizationIntention();
    assertTrue(intention.isAvailable(getProject(), getEditor(), getFile()));
    intention.invoke(getProject(), getEditor(), getFile());
    myFixture.checkResultByFile("Intention_after.java");
    assertFalse(intention.isAvailable(getProject(), getEditor(), getFile()));
  }

  private void doTest(boolean fix) {
    myFixture.testHighlighting(getTestName(false) + ".java");
    if (!fix) return;

    final IntentionAction action = myFixture.filterAvailableIntentions("Properly capitalize").get(0);
    WriteCommandAction.writeCommandAction(getProject()).run(() -> action.invoke(getProject(), myFixture.getEditor(), getFile()));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.codeInspection; public class CommonProblemDescriptor {}");
    myFixture.addClass("package com.intellij.codeInspection; public class QuickFix {}");
    myFixture.enableInspections(TitleCapitalizationInspection.class);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/inspections/capitalization";
  }
}
