// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrAllPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrArgPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrCastExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrElsePostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrFilterPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrFlatMapPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrFoldLeftPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrForeachPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrIfNotNullExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrIfNullExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrIfPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrIntroduceVariablePostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrMapPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrNegateBooleanPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrNewExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrParenthesizedExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrReducePostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrReqnonnullExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrReturnExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrSerrExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrSoutExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrThrowExpressionPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrTryPostfixTemplate;
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrWhilePostfixTemplate;

import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.groovy.util.GroovyFixture.configureByExactName;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyPostfixTemplatesTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.JAVA_LATEST);
  private CompletionAutoPopupTester myTester;

  @BeforeEach
  public void setUp() {
    myTester = new CompletionAutoPopupTester(groovy.get());
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true, groovy.get().getTestRootDisposable());
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, groovy.get().getTestRootDisposable());
  }

  @AfterEach
  public void tearDown() {
    // The base resets the stamp after each test, so that an invokeLater of the auto-popup handler cancels itself.
    Editor editor = groovy.get().getEditor();
    if (editor != null) {
      ((DocumentEx)editor.getDocument()).setModificationStamp(0);
    }
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    assertNotNull(settings);
    settings.setProviderToDisabledTemplates(new HashMap<>());
    settings.setPostfixTemplatesEnabled(true);
    settings.setTemplatesCompletionEnabled(true);
  }

  private void type(String s) {
    myTester.typeWithPauses(s);
  }

  private void doGeneralAutoPopupTest(@NotNull String baseText,
                                      @NotNull final String textToType,
                                      @Nullable String result,
                                      @NotNull Class<? extends PostfixTemplate> expectedClass,
                                      boolean invokeRefactoring) {
    configureByExactName(groovy.get(), "_.groovy", baseText);
    type(textToType);
    final LookupEx lookup = groovy.get().getLookup();
    assertNotNull(lookup);
    final LookupElement item = lookup.getItems().stream().filter(e -> e.getLookupString().startsWith("." + textToType)).findFirst().orElse(null);
    assertNotNull(item);
    assertInstanceOf(PostfixTemplateLookupElement.class, item);
    assertInstanceOf(expectedClass, ((PostfixTemplateLookupElement)item).getPostfixTemplate());
    ApplicationManager.getApplication().invokeAndWait(
      () -> ApplicationManager.getApplication().runWriteAction(
        () ->lookup.setCurrentItem(item)));
    if (invokeRefactoring) {
      type(" ");
      groovy.get().checkResult(result);
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
    configureByExactName(groovy.get(), "_.groovy", baseText);
    type(textToType);
    Lookup lookup = groovy.get().getLookup();
    if (lookup != null) {
      LookupElement item = lookup.getCurrentItem();
      assertNotNull(item);
      assertTrue(!(item instanceof PostfixTemplateLookupElement) ||
                          !clazz.isInstance(((PostfixTemplateLookupElement)item).getPostfixTemplate()));
    }
  }

  @Test
  public void testArg() {
    doAutoPopupTest("foo().<caret>", "arg", "(foo())", GrArgPostfixTemplate.class);
  }

  @Test
  public void testPar() {
    doAutoPopupTest("1.<caret>", "par", "(1)", GrParenthesizedExpressionPostfixTemplate.class);
  }

  @Test
  public void testCast() {
    doAutoPopupTest("1.<caret>", "cast", "1 as <caret>", GrCastExpressionPostfixTemplate.class);
  }

  @Test
  public void testFor() {
    doAutoPopupTest("[1, 2, 3].<caret>", "for", "for (final def  in [1, 2, 3]) {\n    \n}", GrForeachPostfixTemplate.class);
  }

  @Test
  public void testIter() {
    doAutoPopupTest("[1, 2, 3].<caret>", "iter", "for (final def  in [1, 2, 3]) {\n    \n}", GrForeachPostfixTemplate.class);
  }

  @Test
  public void testNoForWithNonIterable() {
    doNoPopupTest("1.<caret>", "for", GrForeachPostfixTemplate.class);
  }

  @Test
  public void testNewWithClass() {
    doAutoPopupTest("String.<caret>", "new", "new String()", GrNewExpressionPostfixTemplate.class);
  }

  @Test
  public void testNewWithCall() {
    doAutoPopupTest("foo().<caret>", "new", "new foo()", GrNewExpressionPostfixTemplate.class);
  }

  @Test
  public void testNoNew() {
    doNoPopupTest("1.<caret>", "new", GrNewExpressionPostfixTemplate.class);
  }

  @Test
  public void testNn() {
    doAutoPopupTest("foo().<caret>", "nn", "if (foo() != null) {\n    \n}", GrIfNotNullExpressionPostfixTemplate.class);
  }

  @Test
  public void testNotnull() {
    doAutoPopupTest("foo().<caret>", "notnull", "if (foo() != null) {\n    \n}", GrIfNotNullExpressionPostfixTemplate.class);
  }

  @Test
  public void testNoNNForPrimitive() {
    doNoPopupTest("int x = 1; x.<caret>", "nn", GrIfNotNullExpressionPostfixTemplate.class);
  }

  @Test
  public void testNull() {
    doAutoPopupTest("foo().<caret>", "null", "if (foo() == null) {\n    \n}", GrIfNullExpressionPostfixTemplate.class);
  }

  @Test
  public void testReqnonnull() {
    doAutoPopupTest("foo.<caret>", "reqnonnull", "Objects.requireNonNull(foo)<caret>", GrReqnonnullExpressionPostfixTemplate.class);
  }

  @Test
  public void testReturn() {
    doAutoPopupTest("def foo() { bar.<caret> }", "return", "def foo() { return bar }", GrReturnExpressionPostfixTemplate.class);
  }

  @Test
  public void testNoReturn() {
    doNoPopupTest("bar.<caret>", "return", GrReturnExpressionPostfixTemplate.class);
  }

  @Test
  public void testSout() {
    doAutoPopupTest("foo.<caret>", "sout", "println foo", GrSoutExpressionPostfixTemplate.class);
  }

  @Test
  public void testSerr() {
    doAutoPopupTest("foo.<caret>", "serr", "System.err.println(foo)", GrSerrExpressionPostfixTemplate.class);
  }

  @Test
  public void testThrow() {
    doAutoPopupTest("new IOException().<caret>", "throw", "throw new IOException()", GrThrowExpressionPostfixTemplate.class);
  }

  @Test
  public void testThrowNotThrowable() {
    doAutoPopupTest("1.<caret>", "throw", "throw new RuntimeException(1)", GrThrowExpressionPostfixTemplate.class);
  }

  @Test
  public void testTry() {
    doAutoPopupTest("foo().<caret>", "try", "try {\n    foo()\n} catch (Exception e) {\n    \n}", GrTryPostfixTemplate.class);
  }

  @Test
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

  @Test
  public void testVar() {
    doAutoPopupTestWithoutInvocation("foo().<caret>", "var", GrIntroduceVariablePostfixTemplate.class);
  }

  @Test
  public void testDef() {
    doAutoPopupTestWithoutInvocation("foo().<caret>", "def", GrIntroduceVariablePostfixTemplate.class);
  }

  @Test
  public void testWhile() {
    doAutoPopupTest("true.<caret>", "while", "while (true) {\n    \n}", GrWhilePostfixTemplate.class);
  }

  @Test
  public void testNot() {
    doAutoPopupTest("true.<caret>", "not", "!true", GrNegateBooleanPostfixTemplate.class);
  }

  @Test
  public void testMap() {
    doAutoPopupTest("[1, 2, 3].<caret>", "map", "[1, 2, 3].collect {}", GrMapPostfixTemplate.class);
  }

  @Test
  public void testAll() {
    doAutoPopupTest("[1, 2, 3].<caret>", "all", "[1, 2, 3].every {}", GrAllPostfixTemplate.class);
  }

  @Test
  public void testFilter() {
    doAutoPopupTest("[1, 2, 3].<caret>", "filter", "[1, 2, 3].findAll {}", GrFilterPostfixTemplate.class);
  }

  @Test
  public void testFlatMap() {
    doAutoPopupTest("[1, 2, 3].<caret>", "flatMap", "[1, 2, 3].collectMany {}", GrFlatMapPostfixTemplate.class);
  }

  @Test
  public void testFoldLeft() {
    doAutoPopupTest("[1, 2, 3].<caret>", "foldLeft", "[1, 2, 3].inject() {}", GrFoldLeftPostfixTemplate.class);
  }

  @Test
  public void testReduce() {
    doAutoPopupTest("[1, 2, 3].<caret>", "reduce", "[1, 2, 3].inject {}", GrReducePostfixTemplate.class);
  }

  @Test
  public void testInClosure() {
    doAutoPopupTest("1.with { [1, 2, 3].<caret> }", "par", "1.with { ([1, 2, 3]) }", GrParenthesizedExpressionPostfixTemplate.class);
  }

  @Test
  public void testInClosure2() {
    doAutoPopupTest("1.with { [1, 2, 3].<caret> }", "map", "1.with { [1, 2, 3].collect {} }", GrMapPostfixTemplate.class);
  }

  @Test
  public void testIf() {
    doAutoPopupTest("true.<caret>", "if", "if (true) {\n    \n}", GrIfPostfixTemplate.class);
  }

  @Test
  public void testElse() {
    doAutoPopupTest("true.<caret>", "else", "if (!true) {\n    \n}", GrElsePostfixTemplate.class);
  }
}
