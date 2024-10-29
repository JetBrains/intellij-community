// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.smartEnter;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class SmartEnterTest extends LightGroovyTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/actions/smartEnter/";
  }

  public void testMethCallComma() { doTest(); }

  public void testMethCallWithArg() { doTest(); }

  public void testMethodCallMissArg() { doTest(); }

  public void testMissBody() { doTest(); }

  public void testMissCondition() { doTest(); }

  public void testMissIfclosureParen() { doTest(); }

  public void testMissIfCurl() { doTest(); }

  public void testMissingIfClosedParenth() { doTest(); }

  public void testMissRParenth() { doTest(); }

  public void testMissRParenthInMethod() { doTest(); }

  public void testMissRQuote() { doTest(); }

  public void testMissRQuoteInCompStr() { doTest(); }

  public void testGotoNextLineInFor() { doTest(); }

  public void testGotoParentInIf() { doTest(); }

  public void testListFixer() { doTest(); }

  public void testSwitchBraces() { doTest(); }

  public void testCatchClause() { doTest(); }

  public void testMethodBodyAtNextLine() {
    CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(GroovyLanguage.INSTANCE).METHOD_BRACE_STYLE =
      CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

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

  public void testSmartEnterInClosureArg() {
    doTextTest("""
                 [1, 2, 3].each<caret>
                 """, """
                 [1, 2, 3].each {
                     <caret>
                 }
                 """);
  }

  public void testSynchronizedBraces() {
    doTextTest("""
                 synchronized(x<caret>)
                 """, """
                 synchronized (x) {
                     <caret>
                 }
                 """);
  }

  public void testClassBody() {
    doTextTest("class X<caret>", """
      class X {
          <caret>
      }""");
  }

  private void doTextTest(String before, String after) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
    myFixture.checkResult(after);
  }

  private void doTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    doTextTest(data.get(0), data.get(1));
  }
}
