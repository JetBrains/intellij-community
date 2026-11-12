// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ExtractMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/extractMethod/");


  private void doAntiTest(String errorMessage) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      GroovyExtractMethodHandler handler = configureFromText(readInput().get(0), "testMethod");
      try {
        handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
        fail();
      }
      catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
        assertEquals(errorMessage, e.getLocalizedMessage());
      }
    });
  }

  private List<String> readInput() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
  }

  private void doTest() {
    doTest("testMethod");
  }

  private void doTest(String name) {
    EdtTestUtil.runInEdtAndWait(() -> {
      final List<String> data = readInput();
      final String before = data.get(0);
      String after = StringUtil.trimEnd(data.get(1), "\n");

      doTest(name, before, after);
    });
  }

  private void doTest(String before, String after) {
    doTest("testMethod", before, after);
  }

  private void doTest(String name, String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      GroovyExtractMethodHandler handler = configureFromText(before, name);
      try {
        handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
        PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
        myFixture.checkResult(after);
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          myFixture.getDocument(myFixture.getFile()).setText(e.getMessage());
          PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
        });

        myFixture.checkResult(after);
      }
    });
  }

  private GroovyExtractMethodHandler configureFromText(String fileText, final String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    return ReadAction.computeBlocking(() -> {
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
    });
  }

  @Test
  public void testIf1() { doTest(); }

  @Test
  public void testCaptured1() { doTest(); }

  @Test
  public void testCaptured2() { doTest(); }

  @Test
  public void testOuterClosureReference() { doTest(); }

  @Test
  public void testClos_em() { doTest(); }

  @Test
  public void testEm1() { doTest(); }

  @Test
  public void testEnum1() { doTest(); }

  @Test
  public void testErr1() { doTest(); }

  @Test
  public void testExpr1() { doTest(); }

  @Test
  public void testExpr2() { doTest(); }

  @Test
  public void testExpr3() { doTest(); }

  @Test
  public void testInput1() { doTest(); }

  @Test
  public void testInput2() { doTest(); }

  @Test
  public void testInter1() { doTest(); }

  @Test
  public void testInter2() { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }

  @Test
  public void testInter3() { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }

  @Test
  public void testInter4() { doTest(); }

  @Test
  public void testMeth_em1() { doTest(); }

  @Test
  public void testMeth_em2() { doTest(); }

  @Test
  public void testMeth_em3() { doTest(); }

  @Test
  public void testOutput1() { doTest(); }

  @Test
  public void testResul1() { doTest(); }

  @Test
  public void testRet1() { doTest(); }

  @Test
  public void testRet2() { doTest(); }

  @Test
  public void testRet3() { doTest(); }

  @Test
  public void testRet4() { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }

  @Test
  public void testVen1() { doTest(); }

  @Test
  public void testVen2() { doTest(); }

  @Test
  public void testVen3() { doTest(); }

  @Test
  public void testForIn() { doTest(); }

  @Test
  public void testInCatch() { doTest(); }

  @Test
  public void testClosureIt() { doTest(); }

  @Test
  public void testImplicitReturn() { doTest(); }

  @Test
  public void testMultiOutput1() { doTest(); }

  @Test
  public void testMultiOutput2() { doTest(); }

  @Test
  public void testMultiOutput3() { doTest(); }

  @Test
  public void testMultiOutput4() { doTest(); }

  @Test
  public void testMultiOutput5() { doTest(); }

  @Test
  public void testDontShortenRefsIncorrect() { doTest(); }

  @Test
  public void testLastBlockStatementInterruptsControlFlow() { doTest(); }

  @Test
  public void testAOOBE() { doTest(); }

  @Test
  public void testWildCardReturnType() { doTest(); }

  @Test
  public void testParamChangedInsideExtractedMethod() { doTest(); }

  @Test
  public void testTerribleAppStatement() { doTest(); }

  @Test
  public void testArgsUsedOnlyInClosure() { doTest(); }

  @Test
  public void testArgsUsedOnlyInAnonymousClass() { doTest(); }

  @Test
  public void testTwoVars() { doTest(); }

  @Test
  public void testContextConflicts() { doTest(); }

  @Test
  public void testNoContextConflicts() { doTest(); }

  @Test
  public void testTupleDeclaration() { doTest(); }

  @Test
  public void testNonIdentifierName() { doTest("f*f"); }

  @Test
  public void testAutoSelectExpression() { doTest(); }

  @Test
  public void testUnassignedVar() { doTest(); }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testClassVsDefaultGDKClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package javax.sound.midi; public class Sequence { }");
    doTest();
  }
}
