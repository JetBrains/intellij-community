// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.psi.PsiType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class IntroduceVariableTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/introduceVariable/");
  }

  @Test
  public void testAbs() {
    doTest();
  }

  @Test
  public void testCall1() {
    doTest();
  }

  @Test
  public void testCall2() {
    doTest();
  }

  @Test
  public void testCall3() {
    doTest();
  }

  @Test
  public void testClos1() {
    doTest();
  }

  @Test
  public void testClos2() {
    doTest();
  }

  @Test
  public void testClos3() {
    doTest();
  }

  @Test
  public void testClos4() {
    doTest();
  }

  @Test
  public void testF2() {
    doTest();
  }

  @Test
  public void testField1() {
    doTest();
  }

  @Test
  public void testFirst() {
    doTest();
  }

  @Test
  public void testIf1() {
    doTest();
  }

  @Test
  public void testIf2() {
    doTest();
  }

  @Test
  public void testLocal1() {
    doTest();
  }

  @Test
  public void testLoop1() {
    doTest();
  }

  @Test
  public void testLoop2() {
    doTest();
  }

  @Test
  public void testLoop3() {
    doTest();
  }

  @Test
  public void testLoop4() {
    doTest();
  }

  @Test
  public void testLoop5() {
    doTest();
  }

  @Test
  public void testLoop6() {
    doTest();
  }

  @Test
  public void testLoop7() {
    doTest();
  }

  @Test
  public void testLoop8() {
    doTest();
  }

  @Test
  public void testInCase() {
    doTest();
  }

  @Test
  public void testCaseLabel() {
    doTest();
  }

  @Test
  public void testLabel1() {
    doTest();
  }

  @Test
  public void testLabel2() {
    doTest();
  }

  @Test
  public void testLabel3() {
    doTest();
  }

  @Test
  public void testDuplicatesInsideIf() {
    doTest();
  }

  @Test
  public void testFromGString() {
    doTest();
  }

  @Test
  public void testCharArray() {
    doTest(true);
  }

  @Test
  public void testCallableProperty() {
    doTest();
  }

  @Test
  public void testFqn() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package p;
                         public class Foo {
                             public static int foo() {
                                 return 1;
                             }
                         }
                         """);
    doTest();
  }

  @Test
  public void testStringPart1() {
    doTest("""
             print 'a<begin>b<end>c'
             """, """
             def preved = 'b'
             print 'a' + preved<caret> + 'c'
             """);
  }

  @Test
  public void testStringPart2() {
    doTest("""
             print "a<begin>b<end>c"
             """, """
             def preved = "b"
             print "a" + preved<caret> + "c"
             """);
  }

  @Test
  public void testAllUsages() {
    doTest("""
             def foo() {
                 println(123);        // (1)
                 println(123);        // (2)
                 if (true) {
                     println(<all>123<end>);    // (3)
                     println(123);    // (4)
                 }
             }
             """, """
             def foo() {
                 def preved = 123
                 println(preved);        // (1)
                 println(preved);        // (2)
                 if (true) {
                     println(preved<caret>);    // (3)
                     println(preved);    // (4)
                 }
             }
             """);
  }

  @Test
  public void testDollarSlashyString() {
    doTest("""
             print($/a<begin>b<end>c/$)
             """, """
             def preved = $/b/$
             print($/a/$ + preved + $/c/$)
             """);
  }

  protected static final String ALL_MARKER = "<all>";

  private void processFile(String fileText, boolean explicitType) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    boolean replaceAllOccurrences = prepareText(fileText);

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiType type = inferType(explicitType);

      final MockSettings settings = new MockSettings(false, "preved", type, replaceAllOccurrences);
      final GrIntroduceVariableHandler introduceVariableHandler = new MockGrIntroduceVariableHandler(settings);

      introduceVariableHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
    });
  }

  private boolean prepareText(@NotNull String fileText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);

    boolean replaceAllOccurrences;
    if (startOffset < 0) {
      startOffset = fileText.indexOf(ALL_MARKER);
      replaceAllOccurrences = true;
      fileText = removeAllMarker(fileText);
    }
    else {
      replaceAllOccurrences = false;
      fileText = TestUtils.removeBeginMarker(fileText);
    }

    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    int selectionStart = startOffset;
    EdtTestUtil.runInEdtAndWait(() -> myFixture.getEditor().getSelectionModel().setSelection(selectionStart, endOffset));
    return replaceAllOccurrences;
  }

  private PsiType inferType(boolean explicitType) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    if (explicitType) {
      final int start = myFixture.getEditor().getSelectionModel().getSelectionStart();
      final int end = myFixture.getEditor().getSelectionModel().getSelectionEnd();
      final GrExpression expression = GrIntroduceHandlerBase.findExpression(myFixture.getFile(), start, end);
      if (expression != null) {
        return expression.getType();
      }
    }
    return null;
  }

  public void doTest(boolean explicitType) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final Iterator<String> iterator = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test").iterator();
    doTest(iterator.next(), iterator.next(), explicitType);
  }

  public void doTest() {
    doTest(false);
  }

  public void doTest(String before, String after, boolean explicitType) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    processFile(before, explicitType);
    myFixture.checkResult(after, true);
  }

  public void doTest(String before, String after) {
    doTest(before, after, false);
  }

  protected static String removeAllMarker(String text) {
    int index = text.indexOf(ALL_MARKER);
    return text.substring(0, index) + text.substring(index + ALL_MARKER.length());
  }
}
