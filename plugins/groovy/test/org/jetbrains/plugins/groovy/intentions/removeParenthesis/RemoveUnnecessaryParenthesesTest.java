// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.removeParenthesis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class RemoveUnnecessaryParenthesesTest extends LightJavaCodeInsightFixtureTestCase {
  public void testRemoveUnnecessaryParenthesis() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.launchAction(UsefulTestCase.assertOneElement(myFixture.filterAvailableIntentions(getINTENTION_NAME())));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testNothingInsideClosure() {
    doTest("println ({ i<caret>t })");
  }

  public void testNamedArgs() {
    doTest();
  }

  public void test_empty_argument_list() {
    doTest("<caret>foo()");
  }

  public void test_empty_argument_list_with_closure_arguments() {
    doTest("<caret>foo() {} {}", "foo {} {}");
  }

  public void test_argument_list_with_closure_arguments() {
    doTest("<caret>foo(1) {} {}");
  }

  public void test_regular() {
    doTest("foo(42)", "foo 42");
  }

  public void test_single_closure_in_argument_list() {
    doTest("<caret>foo({})", "foo {}");
  }

  public void test_closure_in_argument_list() {
    doTest("<caret>foo({}, 1, 2)");
  }

  public void test_spread_argument() {
    doTest("<caret>foo(3, *[], 5)");
  }

  public void test_slashy_argument() {
    doTest("<caret>foo(/1/)");
  }

  public void test_dollar_slashy_argument() {
    doTest("<caret>foo($/1/$)");
  }

  public void test_initializer() {
    doTest("def a = <caret>foo(33)", "def a = foo 33");
  }

  public void test_rhs_of_assignment() {
    doTest("a = <caret>foo(1)", "a = foo 1");
  }

  public void test_list_literal_argument() {
    doTest("<caret>foo([])");
  }

  public void test_list_literal_with_content_argument() {
    doTest("<caret>foo([1])");
  }

  public void test_map_literal_with_content_argument() {
    doTest("<caret>foo([a: 1])");
  }

  private void doTest(String before, String after) {
    myFixture.configureByText("_.groovy", before);
    List<IntentionAction> actions = myFixture.filterAvailableIntentions(getINTENTION_NAME());
    if (after == null) {
      assertTrue(actions.isEmpty());
    }
    else {
      myFixture.launchAction(UsefulTestCase.assertOneElement(actions));
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      myFixture.checkResult(after);
    }
  }

  private void doTest(String before) {
    doTest(before, null);
  }

  private static String getINTENTION_NAME() {
    return GroovyIntentionsBundle.message("remove.parentheses.from.method.call.intention.name");
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = TestUtils.getTestDataPath() + "intentions/removeParenth/";
}
