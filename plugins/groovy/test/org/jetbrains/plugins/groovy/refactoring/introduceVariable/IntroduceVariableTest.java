// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Iterator;

public class IntroduceVariableTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/introduceVariable/";
  }

  public void testAbs() { doTest(); }

  public void testCall1() { doTest(); }

  public void testCall2() { doTest(); }

  public void testCall3() { doTest(); }

  public void testClos1() { doTest(); }

  public void testClos2() { doTest(); }

  public void testClos3() { doTest(); }

  public void testClos4() { doTest(); }

  public void testF2() { doTest(); }

  public void testField1() { doTest(); }

  public void testFirst() { doTest(); }

  public void testIf1() { doTest(); }

  public void testIf2() { doTest(); }

  public void testLocal1() { doTest(); }

  public void testLoop1() { doTest(); }

  public void testLoop2() { doTest(); }

  public void testLoop3() { doTest(); }

  public void testLoop4() { doTest(); }

  public void testLoop5() { doTest(); }

  public void testLoop6() { doTest(); }

  public void testLoop7() { doTest(); }

  public void testLoop8() { doTest(); }

  public void testInCase() { doTest(); }

  public void testCaseLabel() { doTest(); }

  public void testLabel1() { doTest(); }

  public void testLabel2() { doTest(); }

  public void testLabel3() { doTest(); }

  public void testDuplicatesInsideIf() { doTest(); }

  public void testFromGString() { doTest(); }

  public void testCharArray() { doTest(true); }

  public void testCallableProperty() { doTest(); }

  public void testFqn() {
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

  public void testStringPart1() {
    doTest("""
             print 'a<begin>b<end>c'
             """, """
             def preved = 'b'
             print 'a' + preved<caret> + 'c'
             """);
  }

  public void testStringPart2() {
    doTest("""
             print "a<begin>b<end>c"
             """, """
             def preved = "b"
             print "a" + preved<caret> + "c"
             """);
  }

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
    boolean replaceAllOccurrences = prepareText(fileText);

    PsiType type = inferType(explicitType);

    final MockSettings settings = new MockSettings(false, "preved", type, replaceAllOccurrences);
    final GrIntroduceVariableHandler introduceVariableHandler = new MockGrIntroduceVariableHandler(settings);

    introduceVariableHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
  }

  private boolean prepareText(@NotNull String fileText) {
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

    myFixture.getEditor().getSelectionModel().setSelection(startOffset, endOffset);
    return replaceAllOccurrences;
  }

  private PsiType inferType(boolean explicitType) {
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
    final Iterator<String> iterator = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test").iterator();
    doTest(iterator.next(), iterator.next(), explicitType);
  }

  public void doTest() {
    doTest(false);
  }

  public void doTest(String before, String after, boolean explicitType) {
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
