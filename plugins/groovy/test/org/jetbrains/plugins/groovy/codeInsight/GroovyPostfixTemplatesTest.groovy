// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.*

class GroovyPostfixTemplatesTest extends JavaCompletionAutoPopupTestCase {

  @Override
  void setUp() throws Exception {
    super.setUp()
    CodeInsightSettings.instance.selectAutopopupSuggestionsByChars = true
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable())
  }

  @Override
  void tearDown() throws Exception {
    CodeInsightSettings.instance.selectAutopopupSuggestionsByChars = false
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance()
    assertNotNull(settings)
    settings.setProviderToDisabledTemplates(new HashMap<>())
    settings.setPostfixTemplatesEnabled(true)
    settings.setTemplatesCompletionEnabled(true)
    super.tearDown()
  }

  private void doGeneralAutoPopupTest(@NotNull String baseText,
                                      @NotNull String textToType,
                                      @Nullable String result,
                                      @NotNull Class<? extends PostfixTemplate> expectedClass,
                                      boolean invokeRefactoring) {
    myFixture.configureByText "_.groovy", baseText
    type textToType
    Lookup lookup = myFixture.getLookup()
    assertNotNull(lookup)
    LookupElement item = lookup.getCurrentItem()
    assertNotNull item
    assertInstanceOf item, PostfixTemplateLookupElement.class
    assertInstanceOf((item as PostfixTemplateLookupElement).getPostfixTemplate(), expectedClass)
    if (invokeRefactoring) {
      type ' '
      myFixture.checkResult result
    }
  }

  private void doAutoPopupTest(@NotNull String baseText,
                               @NotNull String textToType,
                               @NotNull String result,
                               @NotNull Class<? extends PostfixTemplate> expectedClass) {
    doGeneralAutoPopupTest(baseText, textToType, result, expectedClass, true)
  }

  private void doAutoPopupTestWithoutInvocation(@NotNull String baseText,
                                                @NotNull String textToType,
                                                @NotNull Class<? extends PostfixTemplate> expectedClass) {
    doGeneralAutoPopupTest(baseText, textToType, null, expectedClass, false)
  }

  private void doNoPopupTest(@NotNull String baseText, @NotNull String textToType, @NotNull Class<? extends PostfixTemplate> clazz) {
    myFixture.configureByText "_.groovy", baseText
    type textToType
    Lookup lookup = myFixture.getLookup()
    if (lookup != null) {
      LookupElement item = lookup.getCurrentItem()
      assertNotNull item
      assertTrue(!(item instanceof PostfixTemplateLookupElement) || !clazz.isInstance(item.postfixTemplate))
    }
  }

  void testArg() {
    doAutoPopupTest "foo().<caret>", "arg", "(foo())", GrArgPostfixTemplate
  }

  void testPar() {
    doAutoPopupTest "1.<caret>", "par", "(1)", GrParenthesizedExpressionPostfixTemplate
  }

  void testCast() {
    doAutoPopupTest "1.<caret>", "cast", "1 as <caret>", GrCastExpressionPostfixTemplate
  }

  void testFor() {
    doAutoPopupTest "[1, 2, 3].<caret>", "for", "for (final def  in [1, 2, 3]) {\n    \n}", GrForeachPostfixTemplate
  }

  void testIter() {
    doAutoPopupTest "[1, 2, 3].<caret>", "iter", "for (final def  in [1, 2, 3]) {\n    \n}", GrForeachPostfixTemplate
  }

  void testNoForWithNonIterable() {
    doNoPopupTest "1.<caret>", "for", GrForeachPostfixTemplate
  }

  void testNewWithClass() {
    doAutoPopupTest "String.<caret>", "new", "new String()", GrNewExpressionPostfixTemplate
  }

  void testNewWithCall() {
    doAutoPopupTest "foo().<caret>", "new", "new foo()", GrNewExpressionPostfixTemplate
  }

  void testNoNew() {
    doNoPopupTest "1.<caret>", "new", GrNewExpressionPostfixTemplate
  }

  void testNn() {
    doAutoPopupTest "foo().<caret>", "nn", "if (foo() != null) {\n    \n}", GrIfNotNullExpressionPostfixTemplate
  }

  void testNotnull() {
    doAutoPopupTest "foo().<caret>", "notnull", "if (foo() != null) {\n    \n}", GrIfNotNullExpressionPostfixTemplate
  }

  void testNoNNForPrimitive() {
    doNoPopupTest "int x = 1; x.<caret>", "nn", GrIfNotNullExpressionPostfixTemplate
  }

  void testNull() {
    doAutoPopupTest "foo().<caret>", "null", "if (foo() == null) {\n    \n}", GrIfNullExpressionPostfixTemplate
  }

  void testReqnonnull() {
    doAutoPopupTest "foo.<caret>", "reqnonnull", "Objects.requireNonNull(foo)<caret>", GrReqnonnullExpressionPostfixTemplate
  }

  void testReturn() {
    doAutoPopupTest "def foo() { bar.<caret> }", "return", "def foo() { return bar }", GrReturnExpressionPostfixTemplate
  }

  void testNoReturn() {
    doNoPopupTest "bar.<caret>", "return", GrReturnExpressionPostfixTemplate
  }

  void testSout() {
    doAutoPopupTest "foo.<caret>", "sout", "println foo", GrSoutExpressionPostfixTemplate
  }

  void testSerr() {
    doAutoPopupTest "foo.<caret>", "serr", "System.err.println(foo)", GrSerrExpressionPostfixTemplate
  }

  void testThrow() {
    doAutoPopupTest "new IOException().<caret>", "throw", "throw new IOException()", GrThrowExpressionPostfixTemplate
  }

  void testThrowNotThrowable() {
    doAutoPopupTest "1.<caret>", "throw", "throw new RuntimeException(1)", GrThrowExpressionPostfixTemplate
  }

  void testTry() {
    doAutoPopupTest "foo().<caret>", "try", "try {\n    foo()\n} catch (Exception e) {\n    \n}", GrTryPostfixTemplate
  }

  void testTryWithExceptions() {
    doAutoPopupTest """\
def foo() throws IOException, IndexOutOfBoundsException {}

foo().<caret>\
""", "try", """\
def foo() throws IOException, IndexOutOfBoundsException {}

try {
    foo()
} catch (IOException | IndexOutOfBoundsException e) {
    
}""", GrTryPostfixTemplate
  }

  void testVar() {
    doAutoPopupTestWithoutInvocation "foo().<caret>", "var", GrIntroduceVariablePostfixTemplate
  }

  void testDef() {
    doAutoPopupTestWithoutInvocation "foo().<caret>", "def", GrIntroduceVariablePostfixTemplate
  }

  void testWhile() {
    doAutoPopupTest "true.<caret>", "while", "while (true) {\n    \n}", GrWhilePostfixTemplate
  }

  void testNot() {
    doAutoPopupTest "true.<caret>", "not", "!true", GrNegateBooleanPostfixTemplate
  }

  void testMap() {
    doAutoPopupTest "[1, 2, 3].<caret>", "map", "[1, 2, 3].collect {}", GrMapPostfixTemplate
  }

  void testAll() {
    doAutoPopupTest "[1, 2, 3].<caret>", "all", "[1, 2, 3].every {}", GrAllPostfixTemplate
  }

  void testFilter() {
    doAutoPopupTest "[1, 2, 3].<caret>", "filter", "[1, 2, 3].findAll {}", GrFilterPostfixTemplate
  }

  void testFlatMap() {
    doAutoPopupTest "[1, 2, 3].<caret>", "flatMap", "[1, 2, 3].collectMany {}", GrFlatMapPostfixTemplate
  }

  void testFoldLeft() {
    doAutoPopupTest "[1, 2, 3].<caret>", "foldLeft", "[1, 2, 3].inject() {}", GrFoldLeftPostfixTemplate
  }

  void testReduce() {
    doAutoPopupTest "[1, 2, 3].<caret>", "reduce", "[1, 2, 3].inject {}", GrReducePostfixTemplate
  }

  void testInClosure() {
    doAutoPopupTest "1.with { [1, 2, 3].<caret> }", "par", "1.with { ([1, 2, 3]) }", GrParenthesizedExpressionPostfixTemplate
  }

  void testInClosure2() {
    doAutoPopupTest "1.with { [1, 2, 3].<caret> }", "map", "1.with { [1, 2, 3].collect {} }", GrMapPostfixTemplate
  }
}
