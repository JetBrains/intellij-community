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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class CapitalizationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/inspections/capitalization");

  @Test
  public void testTitleCapitalization() {
    doTest(true);
  }
  
  @Test
  public void testTitleCapitalizationHtml() {
    doTest(true);
  }

  @Test
  public void testTitleCapitalizationExtension() {
    doTest(true);
  }

  @Test
  public void testSentenceCapitalization() {
    doTest(true);
  }
  
  @Test
  public void testSentenceCapitalizationHtml() {
    doTest(true);
  }

  @Test
  public void testMnemonics() {
    doTest(true);
  }

  @Test
  public void testTernaryAndParentheses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testMultipleReturns() {
    doTest(true);
  }

  @Test
  public void testEmptySentence() {
    doTest(false);
  }

  @Test
  public void testArgument() {
    doTest(false);
  }

  @Test
  public void testConstructorArgument() {
    doTest(false);
  }

  @Test
  public void testSuperConstructorArgument() {
    doTest(false);
  }
  
  @Test
  public void testStripHtml() {
    doTest(false);
  }
  
  @Test
  public void testCapitalizationMismatch() {
    doTest(false);
  }
  
  @Test
  public void testCapitalizationMix() {
    doTest(false);
  }
  
  @Test
  public void testLocalVar() {
    doTest(false);
  }

  @Test
  public void testField() {
    doTest(false);
  }

  @Test
  public void testIgnoreConcatFormat() {
    doTest(false);
  }

  @Test
  public void testPropertyTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testRecursiveMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
    assertThat(myFixture.filterAvailableIntentions("Properly capitalize")).isNullOrEmpty();
  }

  @Test
  public void testIntention() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("Intention.java");
    AnnotateCapitalizationIntention intention = new AnnotateCapitalizationIntention();
    EdtTestUtil.runInEdtAndWait(() -> {
      ActionContext context = ActionContext.from(myFixture.getEditor(), myFixture.getFile());
      assertNotNull(intention.getPresentation(context));
      ModCommandExecutor.executeInteractively(context, "", myFixture.getEditor(), () -> intention.perform(context));
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile("Intention_after.java");
      assertNull(intention.getPresentation(context));
    });
  }

  private void doTest(boolean fix) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
    if (!fix) return;

    final IntentionAction action = myFixture.filterAvailableIntentions("Properly capitalize").get(0);
    EdtTestUtil.runInEdtAndWait(() -> {
      WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.codeInspection; public class CommonProblemDescriptor {}");
    myFixture.addClass("package com.intellij.codeInspection; public class QuickFix {}");
    myFixture.enableInspections(TitleCapitalizationInspection.class);
  }
}
