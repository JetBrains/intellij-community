// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.removeParenthesis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RemoveUnnecessaryParenthesesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "intentions/removeParenth/");
  }

  @Test
  public void testRemoveUnnecessaryParenthesis() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    myFixture.launchAction(assertThat(myFixture.filterAvailableIntentions(getINTENTION_NAME())).singleElement().actual());
    PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }

  @Test
  public void testNothingInsideClosure() {
    doTest("println ({ i<caret>t })");
  }

  @Test
  public void testNamedArgs() {
    doTest();
  }

  @Test
  public void test_empty_argument_list() {
    doTest("<caret>foo()");
  }

  @Test
  public void test_empty_argument_list_with_closure_arguments() {
    doTest("<caret>foo() {} {}", "foo {} {}");
  }

  @Test
  public void test_argument_list_with_closure_arguments() {
    doTest("<caret>foo(1) {} {}");
  }

  @Test
  public void test_regular() {
    doTest("foo(42)", "foo 42");
  }

  @Test
  public void test_single_closure_in_argument_list() {
    doTest("<caret>foo({})", "foo {}");
  }

  @Test
  public void test_closure_in_argument_list() {
    doTest("<caret>foo({}, 1, 2)");
  }

  @Test
  public void test_spread_argument() {
    doTest("<caret>foo(3, *[], 5)");
  }

  @Test
  public void test_slashy_argument() {
    doTest("<caret>foo(/1/)");
  }

  @Test
  public void test_dollar_slashy_argument() {
    doTest("<caret>foo($/1/$)");
  }

  @Test
  public void test_initializer() {
    doTest("def a = <caret>foo(33)", "def a = foo 33");
  }

  @Test
  public void test_rhs_of_assignment() {
    doTest("a = <caret>foo(1)", "a = foo 1");
  }

  @Test
  public void test_list_literal_argument() {
    doTest("<caret>foo([])");
  }

  @Test
  public void test_list_literal_with_content_argument() {
    doTest("<caret>foo([1])");
  }

  @Test
  public void test_map_literal_with_content_argument() {
    doTest("<caret>foo([a: 1])");
  }

  private void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", before);
    List<IntentionAction> actions = myFixture.filterAvailableIntentions(getINTENTION_NAME());
    if (after == null) {
      assertTrue(actions.isEmpty());
    }
    else {
      myFixture.launchAction(assertThat(actions).singleElement().actual());
      PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
      myFixture.checkResult(after);
    }
  }

  private void doTest(String before) {
    doTest(before, null);
  }

  private static String getINTENTION_NAME() {
    return GroovyIntentionsBundle.message("remove.parentheses.from.method.call.intention.name");
  }
}
