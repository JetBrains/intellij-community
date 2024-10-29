// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class ExtractMethodTest extends LightGroovyTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  private void doAntiTest(String errorMessage) {
    GroovyExtractMethodHandler handler = configureFromText(readInput().get(0), "testMethod");
    try {
      handler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      fail();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(errorMessage, e.getLocalizedMessage());
    }
  }

  private List<String> readInput() {
    return TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
  }

  private void doTest() {
    doTest("testMethod");
  }

  private void doTest(String name) {
    final List<String> data = readInput();
    final String before = data.get(0);
    String after = StringUtil.trimEnd(data.get(1), "\n");

    doTest(name, before, after);
  }

  private void doTest(String before, String after) {
    doTest("testMethod", before, after);
  }

  private void doTest(String name, String before, String after) {
    GroovyExtractMethodHandler handler = configureFromText(before, name);
    try {
      handler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      myFixture.checkResult(after);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        myFixture.getDocument(myFixture.getFile()).setText(e.getMessage());
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      });

      myFixture.checkResult(after);
    }
  }

  private GroovyExtractMethodHandler configureFromText(String fileText, final String name) {
    final int caret = fileText.indexOf(TestUtils.CARET_MARKER);
    if (caret >= 0) {
      myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);
    }
    else {
      int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
      fileText = TestUtils.removeBeginMarker(fileText);
      int endOffset = fileText.indexOf(TestUtils.END_MARKER);
      fileText = TestUtils.removeEndMarker(fileText);
      myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);
      myFixture.getEditor().getSelectionModel().setSelection(startOffset, endOffset);
    }

    return new GroovyExtractMethodHandler() {
      @Override
      protected ExtractMethodInfoHelper getSettings(@NotNull InitialInfo initialInfo, PsiClass owner) {
        final ExtractMethodInfoHelper helper = new ExtractMethodInfoHelper(initialInfo, name, owner, true);
        final PsiType type = helper.getOutputType();
        if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || PsiTypes.voidType().equals(type)) {
          helper.setSpecifyType(false);
        }
        return helper;
      }
    };
  }

  public void testIf1() { doTest(); }

  public void testCaptured1() { doTest(); }

  public void testCaptured2() { doTest(); }

  public void testOuterClosureReference() { doTest(); }

  public void testClos_em() { doTest(); }

  public void testEm1() { doTest(); }

  public void testEnum1() { doTest(); }

  public void testErr1() { doTest(); }

  public void testExpr1() { doTest(); }

  public void testExpr2() { doTest(); }

  public void testExpr3() { doTest(); }

  public void testInput1() { doTest(); }

  public void testInput2() { doTest(); }

  public void testInter1() { doTest(); }

  public void testInter2() { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }

  public void testInter3() { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }

  public void testInter4() { doTest(); }

  public void testMeth_em1() { doTest(); }

  public void testMeth_em2() { doTest(); }

  public void testMeth_em3() { doTest(); }

  public void testOutput1() { doTest(); }

  public void testResul1() { doTest(); }

  public void testRet1() { doTest(); }

  public void testRet2() { doTest(); }

  public void testRet3() { doTest(); }

  public void testRet4() { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }

  public void testVen1() { doTest(); }

  public void testVen2() { doTest(); }

  public void testVen3() { doTest(); }

  public void testForIn() { doTest(); }

  public void testInCatch() { doTest(); }

  public void testClosureIt() { doTest(); }

  public void testImplicitReturn() { doTest(); }

  public void testMultiOutput1() { doTest(); }

  public void testMultiOutput2() { doTest(); }

  public void testMultiOutput3() { doTest(); }

  public void testMultiOutput4() { doTest(); }

  public void testMultiOutput5() { doTest(); }

  public void testDontShortenRefsIncorrect() { doTest(); }

  public void testLastBlockStatementInterruptsControlFlow() { doTest(); }

  public void testAOOBE() { doTest(); }

  public void testWildCardReturnType() { doTest(); }

  public void testParamChangedInsideExtractedMethod() { doTest(); }

  public void testTerribleAppStatement() { doTest(); }

  public void testArgsUsedOnlyInClosure() { doTest(); }

  public void testArgsUsedOnlyInAnonymousClass() { doTest(); }

  public void testTwoVars() { doTest(); }

  public void testContextConflicts() { doTest(); }

  public void testNoContextConflicts() { doTest(); }

  public void testTupleDeclaration() { doTest(); }

  public void testNonIdentifierName() { doTest("f*f"); }

  public void testAutoSelectExpression() { doTest(); }

  public void testUnassignedVar() { doTest(); }

  public void testForInLoop() {
    boolean registryValue = Registry.is(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE);
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
    try {
      doTest();
    }
    finally {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(registryValue);
    }
  }

  public void testStringPart0() {
    doTest("""
             def foo() {
                 print 'a<begin>b<end>c'
             }
             """, """
             def foo() {
                 print 'a' +<caret> testMethod() + 'c'
             }
             
             private String testMethod() {
                 return 'b'
             }
             """);
  }

  public void testSingleExpressionAsReturnValue() {
    doTest("""
             int foo() {
                 <begin>1<end>
             }
             """, """
             int foo() {
                 testMethod()
             }
             
             private int testMethod() {
                 return 1
             }
             """);
  }

  public void testExtractMethodFromStaticFieldClosureInitializer() {
    doTest("""
             class Foo {
                 static constraints = {
                     bar validator: { val, obj ->
                         <begin>println "validating ${obj}.$val"<end>
                     }
                 }
             }
             """, """
             class Foo {
                 static constraints = {
                     bar validator: { val, obj ->
                         testMethod(obj, val)
                     }
                 }
             
                 private static testMethod(obj, val) {
                     println "validating ${obj}.$val"
                 }
             }
             """);
  }

  public void testExtractMethodFromStaticFieldInitializer() {
    doTest("""
             class Foo {
                 static constraints = <begin>2<end>
             }
             """, """
             class Foo {
                 static constraints = testMethod()
             
                 private static int testMethod() {
                     return 2
                 }
             }
             """);
  }

  public void testExtractMethodFromStringPart() {
    doTest("-", """
      print 'a<begin>b<end>c'
      """, """
             print 'a' + '-'() + 'c'
             
             private String '-'() {
                 return 'b'
             }
             """);
  }

  public void testClassVsDefaultGDKClass() {
    myFixture.addClass("package javax.sound.midi; public class Sequence { }");
    doTest();
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/extractMethod/";
  }
}
