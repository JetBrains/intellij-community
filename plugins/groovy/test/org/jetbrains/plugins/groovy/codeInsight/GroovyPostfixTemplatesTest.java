// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.*;

import java.util.HashMap;

public class GroovyPostfixTemplatesTest extends JavaCompletionAutoPopupTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable());
  }

  @Override
  public void tearDown() throws Exception {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    TestCase.assertNotNull(settings);
    settings.setProviderToDisabledTemplates(new HashMap<>());
    settings.setPostfixTemplatesEnabled(true);
    settings.setTemplatesCompletionEnabled(true);
    super.tearDown();
  }

  private void doGeneralAutoPopupTest(@NotNull String baseText,
                                      @NotNull final String textToType,
                                      @Nullable String result,
                                      @NotNull Class<? extends PostfixTemplate> expectedClass,
                                      boolean invokeRefactoring) {
    myFixture.configureByText("_.groovy", baseText);
    type(textToType);
    final LookupEx lookup = myFixture.getLookup();
    TestCase.assertNotNull(lookup);
    final LookupElement item = lookup.getItems().stream().filter(e -> e.getLookupString().startsWith("." + textToType)).findFirst().orElse(null);
    TestCase.assertNotNull(item);
    UsefulTestCase.assertInstanceOf(item, PostfixTemplateLookupElement.class);
    UsefulTestCase.assertInstanceOf(((PostfixTemplateLookupElement)item).getPostfixTemplate(), (Class<? extends PostfixTemplate>)expectedClass);
    ApplicationManager.getApplication().invokeAndWait(
      () -> ApplicationManager.getApplication().runWriteAction(
        () ->lookup.setCurrentItem(item)));
    if (invokeRefactoring) {
      type(" ");
      myFixture.checkResult(result);
    }
  }

  private void doAutoPopupTest(@NotNull String baseText,
                               @NotNull String textToType,
                               @NotNull String result,
                               @NotNull Class<? extends PostfixTemplate> expectedClass) {
    doGeneralAutoPopupTest(baseText, textToType, result, expectedClass, true);
  }

  private void doAutoPopupTestWithoutInvocation(@NotNull String baseText,
                                                @NotNull String textToType,
                                                @NotNull Class<? extends PostfixTemplate> expectedClass) {
    doGeneralAutoPopupTest(baseText, textToType, null, expectedClass, false);
  }

  private void doNoPopupTest(@NotNull String baseText, @NotNull String textToType, @NotNull Class<? extends PostfixTemplate> clazz) {
    myFixture.configureByText("_.groovy", baseText);
    type(textToType);
    Lookup lookup = myFixture.getLookup();
    if (lookup != null) {
      LookupElement item = lookup.getCurrentItem();
      TestCase.assertNotNull(item);
      TestCase.assertTrue(!(item instanceof PostfixTemplateLookupElement) ||
                          !clazz.isInstance(((PostfixTemplateLookupElement)item).getPostfixTemplate()));
    }
  }

  public void testArg() {
    doAutoPopupTest("foo().<caret>", "arg", "(foo())", GrArgPostfixTemplate.class);
  }

  public void testPar() {
    doAutoPopupTest("1.<caret>", "par", "(1)", GrParenthesizedExpressionPostfixTemplate.class);
  }

  public void testCast() {
    doAutoPopupTest("1.<caret>", "cast", "1 as <caret>", GrCastExpressionPostfixTemplate.class);
  }

  public void testFor() {
    doAutoPopupTest("[1, 2, 3].<caret>", "for", "for (final def  in [1, 2, 3]) {\n    \n}", GrForeachPostfixTemplate.class);
  }

  public void testIter() {
    doAutoPopupTest("[1, 2, 3].<caret>", "iter", "for (final def  in [1, 2, 3]) {\n    \n}", GrForeachPostfixTemplate.class);
  }

  public void testNoForWithNonIterable() {
    doNoPopupTest("1.<caret>", "for", GrForeachPostfixTemplate.class);
  }

  public void testNewWithClass() {
    doAutoPopupTest("String.<caret>", "new", "new String()", GrNewExpressionPostfixTemplate.class);
  }

  public void testNewWithCall() {
    doAutoPopupTest("foo().<caret>", "new", "new foo()", GrNewExpressionPostfixTemplate.class);
  }

  public void testNoNew() {
    doNoPopupTest("1.<caret>", "new", GrNewExpressionPostfixTemplate.class);
  }

  public void testNn() {
    doAutoPopupTest("foo().<caret>", "nn", "if (foo() != null) {\n    \n}", GrIfNotNullExpressionPostfixTemplate.class);
  }

  public void testNotnull() {
    doAutoPopupTest("foo().<caret>", "notnull", "if (foo() != null) {\n    \n}", GrIfNotNullExpressionPostfixTemplate.class);
  }

  public void testNoNNForPrimitive() {
    doNoPopupTest("int x = 1; x.<caret>", "nn", GrIfNotNullExpressionPostfixTemplate.class);
  }

  public void testNull() {
    doAutoPopupTest("foo().<caret>", "null", "if (foo() == null) {\n    \n}", GrIfNullExpressionPostfixTemplate.class);
  }

  public void testReqnonnull() {
    doAutoPopupTest("foo.<caret>", "reqnonnull", "Objects.requireNonNull(foo)<caret>", GrReqnonnullExpressionPostfixTemplate.class);
  }

  public void testReturn() {
    doAutoPopupTest("def foo() { bar.<caret> }", "return", "def foo() { return bar }", GrReturnExpressionPostfixTemplate.class);
  }

  public void testNoReturn() {
    doNoPopupTest("bar.<caret>", "return", GrReturnExpressionPostfixTemplate.class);
  }

  public void testSout() {
    doAutoPopupTest("foo.<caret>", "sout", "println foo", GrSoutExpressionPostfixTemplate.class);
  }

  public void testSerr() {
    doAutoPopupTest("foo.<caret>", "serr", "System.err.println(foo)", GrSerrExpressionPostfixTemplate.class);
  }

  public void testThrow() {
    doAutoPopupTest("new IOException().<caret>", "throw", "throw new IOException()", GrThrowExpressionPostfixTemplate.class);
  }

  public void testThrowNotThrowable() {
    doAutoPopupTest("1.<caret>", "throw", "throw new RuntimeException(1)", GrThrowExpressionPostfixTemplate.class);
  }

  public void testTry() {
    doAutoPopupTest("foo().<caret>", "try", "try {\n    foo()\n} catch (Exception e) {\n    \n}", GrTryPostfixTemplate.class);
  }

  public void testTryWithExceptions() {
    doAutoPopupTest("""
                      def foo() throws IOException, IndexOutOfBoundsException {}
                      
                      foo().<caret>""", "try", """
                      def foo() throws IOException, IndexOutOfBoundsException {}
                      
                      try {
                          foo()
                      } catch (IOException | IndexOutOfBoundsException e) {
                         \s
                      }""", GrTryPostfixTemplate.class);
  }

  public void testVar() {
    doAutoPopupTestWithoutInvocation("foo().<caret>", "var", GrIntroduceVariablePostfixTemplate.class);
  }

  public void testDef() {
    doAutoPopupTestWithoutInvocation("foo().<caret>", "def", GrIntroduceVariablePostfixTemplate.class);
  }

  public void testWhile() {
    doAutoPopupTest("true.<caret>", "while", "while (true) {\n    \n}", GrWhilePostfixTemplate.class);
  }

  public void testNot() {
    doAutoPopupTest("true.<caret>", "not", "!true", GrNegateBooleanPostfixTemplate.class);
  }

  public void testMap() {
    doAutoPopupTest("[1, 2, 3].<caret>", "map", "[1, 2, 3].collect {}", GrMapPostfixTemplate.class);
  }

  public void testAll() {
    doAutoPopupTest("[1, 2, 3].<caret>", "all", "[1, 2, 3].every {}", GrAllPostfixTemplate.class);
  }

  public void testFilter() {
    doAutoPopupTest("[1, 2, 3].<caret>", "filter", "[1, 2, 3].findAll {}", GrFilterPostfixTemplate.class);
  }

  public void testFlatMap() {
    doAutoPopupTest("[1, 2, 3].<caret>", "flatMap", "[1, 2, 3].collectMany {}", GrFlatMapPostfixTemplate.class);
  }

  public void testFoldLeft() {
    doAutoPopupTest("[1, 2, 3].<caret>", "foldLeft", "[1, 2, 3].inject() {}", GrFoldLeftPostfixTemplate.class);
  }

  public void testReduce() {
    doAutoPopupTest("[1, 2, 3].<caret>", "reduce", "[1, 2, 3].inject {}", GrReducePostfixTemplate.class);
  }

  public void testInClosure() {
    doAutoPopupTest("1.with { [1, 2, 3].<caret> }", "par", "1.with { ([1, 2, 3]) }", GrParenthesizedExpressionPostfixTemplate.class);
  }

  public void testInClosure2() {
    doAutoPopupTest("1.with { [1, 2, 3].<caret> }", "map", "1.with { [1, 2, 3].collect {} }", GrMapPostfixTemplate.class);
  }

  public void testIf() {
    doAutoPopupTest("true.<caret>", "if", "if (true) {\n    \n}", GrIfPostfixTemplate.class);
  }

  public void testElse() {
    doAutoPopupTest("true.<caret>", "else", "if (!true) {\n    \n}", GrElsePostfixTemplate.class);
  }
}
