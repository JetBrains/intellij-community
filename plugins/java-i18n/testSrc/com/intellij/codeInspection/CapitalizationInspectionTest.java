// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.capitalization.AnnotateCapitalizationIntention;
import com.intellij.codeInspection.i18n.TitleCapitalizationInspection;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
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

  public void testTitleCapitalizationExtension() {
    doTest(true);
  }

  public void testSentenceCapitalization() {
    doTest(true);
  }

  public void testMnemonics() {
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
  
  public void testStripHtml() {
    doTest(false);
  }
  
  public void testCapitalizationMismatch() {
    doTest(false);
  }
  
  public void testCapitalizationMix() {
    doTest(false);
  }
  
  public void testLocalVar() {
    doTest(false);
  }

  public void testField() {
    doTest(false);
  }

  public void testIgnoreConcatFormat() {
    doTest(false);
  }

  public void testPropertyTest() {
    String props = """
      property.lowercase=hello world
      property.titlecase=Hello World
      property.titlecase.html=<html><b>Hello</b> World</html>
      property.parameterized=Hello {0}
      property.choice.title=Hello {0,choice,0#World|1#Universe}
      property.choice.mixed=Hello {0,choice,0#World|1#universe}
      property.choice.lower=Hello {0,choice,0#world|1#universe}
      property.choice.sentence.start={0,choice,0#No|1#{0}} {0,choice,0#occurrences|1#occurrence|2#occurrences} found so far
      property.sentence.with.quote='return' is not allowed here
      property.with.underscore.mnemonic=Subm_it
      property.icu4j.title=Generate Code with {0, plural, one {Foo} other {Bar}}""";
    myFixture.addFileToProject("MyBundle.properties", props);
    doTest(false);
    IntentionAction action = myFixture.findSingleIntention("Properly capitalize");
    String expected = """
      property.lowercase=Hello World
      property.titlecase=Hello World
      property.titlecase.html=<html><b>Hello</b> World</html>
      property.parameterized=Hello {0}
      property.choice.title=Hello {0,choice,0#World|1#Universe}
      property.choice.mixed=Hello {0,choice,0#World|1#universe}
      property.choice.lower=Hello {0,choice,0#world|1#universe}
      property.choice.sentence.start={0,choice,0#No|1#{0}} {0,choice,0#occurrences|1#occurrence|2#occurrences} found so far
      property.sentence.with.quote='return' is not allowed here
      property.with.underscore.mnemonic=Subm_it
      property.icu4j.title=Generate Code with {0, plural, one {Foo} other {Bar}}""";
    assertEquals(expected, myFixture.getIntentionPreviewText(action));
    myFixture.testHighlighting("PropertyTest2.java");
    action = myFixture.findSingleIntention("Properly capitalize");
    assertEquals(expected, myFixture.getIntentionPreviewText(action));
  }

  public void testRecursiveMethod() {
    myFixture.testHighlighting(getTestName(false) + ".java");
    assertEmpty(myFixture.filterAvailableIntentions("Properly capitalize"));
  }

  public void testIntention() {
    myFixture.configureByFile("Intention.java");
    AnnotateCapitalizationIntention intention = new AnnotateCapitalizationIntention();
    ActionContext context = ActionContext.from(getEditor(), getFile());
    assertNotNull(intention.getPresentation(context));
    ModCommandExecutor.executeInteractively(context, "", getEditor(), () -> intention.perform(context));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile("Intention_after.java");
    assertNull(intention.getPresentation(context));
  }

  private void doTest(boolean fix) {
    myFixture.testHighlighting(getTestName(false) + ".java");
    if (!fix) return;

    final IntentionAction action = myFixture.filterAvailableIntentions("Properly capitalize").get(0);
    WriteCommandAction.writeCommandAction(getProject()).run(() -> action.invoke(getProject(), myFixture.getEditor(), getFile()));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
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
