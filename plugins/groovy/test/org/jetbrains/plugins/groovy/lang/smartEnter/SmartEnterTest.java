// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.smartEnter;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class SmartEnterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/actions/smartEnter/");

  @Test
  public void testMethCallComma() { doTest(); }

  @Test
  public void testMethCallWithArg() { doTest(); }

  @Test
  public void testMethodCallMissArg() { doTest(); }

  @Test
  public void testMissBody() { doTest(); }

  @Test
  public void testMissCondition() { doTest(); }

  @Test
  public void testMissIfclosureParen() { doTest(); }

  @Test
  public void testMissIfCurl() { doTest(); }

  @Test
  public void testMissingIfClosedParenth() { doTest(); }

  @Test
  public void testMissRParenth() { doTest(); }

  @Test
  public void testMissRParenthInMethod() { doTest(); }

  @Test
  public void testMissRQuote() { doTest(); }

  @Test
  public void testMissRQuoteInCompStr() { doTest(); }

  @Test
  public void testGotoNextLineInFor() { doTest(); }

  @Test
  public void testGotoParentInIf() { doTest(); }

  @Test
  public void testListFixer() { doTest(); }

  @Test
  public void testSwitchBraces() { doTest(); }

  @Test
  public void testCatchClause() { doTest(); }

  @Test
  public void testMethodBodyAtNextLine() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(GroovyLanguage.INSTANCE).METHOD_BRACE_STYLE =
      CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  @Test
  public void testReturnMethodCall() {
    doTextTest("""
                 class Foo {
                     def bar
                 
                     def h(Foo o) {
                         return compareTo(o.bar<caret>
                     }
                 }
                 """, """
                 class Foo {
                     def bar
                 
                     def h(Foo o) {
                         return compareTo(o.bar)
                         <caret>
                     }
                 }
                 """);
  }

  @Test
  public void testSmartEnterInClosureArg() {
    doTextTest("""
                 [1, 2, 3].each<caret>
                 """, """
                 [1, 2, 3].each {
                     <caret>
                 }
                 """);
  }

  @Test
  public void testSynchronizedBraces() {
    doTextTest("""
                 synchronized(x<caret>)
                 """, """
                 synchronized (x) {
                     <caret>
                 }
                 """);
  }

  @Test
  public void testClassBody() {
    doTextTest("class X<caret>", """
      class X {
          <caret>
      }""");
  }

  private void doTextTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
    myFixture.checkResult(after);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    doTextTest(data.get(0), data.get(1));
  }
}
