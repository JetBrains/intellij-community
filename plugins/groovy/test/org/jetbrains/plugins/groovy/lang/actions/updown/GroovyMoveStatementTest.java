// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.actions.updown;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.actions.GroovyEditorActionTestUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovyMoveStatementTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/actions/moveStatement/");

  @Test
  public void testClazz2() { bothTest(); }

  @Test
  public void testClos2() { bothTest(); }

  @Test
  public void testMeth1() { bothTest(); }

  @Test
  public void testMeth2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));
    GroovyEditorActionTestUtil.performAction(myFixture, IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
    myFixture.checkResult(data.get(1));
  }

  @Test
  public void testMeth3() { bothTest(); }
  @Test
  public void testMeth4() { bothTest(); }

  @Test
  public void testIfst() { bothTest(); }
  @Test
  public void testIfst2() { bothTest(); }

  @Test
  public void testSimple1() { bothTest(); }
  @Test
  public void testSimple2() { bothTest(); }

  @Test
  public void testTryst1() { bothTest(); }
  @Test
  public void testTryst2() { bothTest(); }

  @Test
  public void testStatementOutsideClosure() { bothTest(); }
  @Test
  public void testVariableOutsideClosure() { bothTest(); }
  @Test
  public void testVariableOutsideClosureDown() { bothTest(); }
  @Test
  public void testStatementInsideClosure() { bothTest(); }

  @Test
  public void testMoveGroovydocWithMethod() { bothTest(); }
  @Test
  public void testLeaveGroovydocWithMethod() { bothTest(); }
  @Test
  public void testMoveMethodWithGroovydoc() { bothTest(); }
  @Test
  public void testMoveGroovyDocWithField() { bothTest(); }

  @Test
  public void testMoveSecondFieldUp() { bothTest(); }
  @Test
  public void testMoveFirstFieldDown() { bothTest(); }

  @Test
  public void testVariableOverMethodInScript() { bothTest(); }
  @Test
  public void testVariableOverClassInScript() { bothTest(); }

  @Test
  public void testUpFromLastOffset() { bothTest(); }

  @Test
  public void testClosureWithPrequel() { bothTest(); }

  @Test
  public void testMultiLineVariable() { bothTest(); }
  @Test
  public void testClosureVariableByRBrace() { bothTest(); }

  @Test
  public void testInsideMultilineString() { bothTest(); }
  @Test
  public void testAroundMultilineString() { bothTest(); }
  @Test
  public void testAroundMultilineString2() { bothTest(); }

  @Test
  public void testInSwitchCaseUp1() { bothTest(); }
  @Test
  public void testInSwitchCaseUp2() { bothTest(); }
  @Test
  public void testInSwitchCaseUp3() { bothTest(); }
  @Test
  public void testInSwitchCaseUp4() { bothTest(); }
  @Test
  public void testInSwitchCaseUp5() { bothTest(); }

  @Test
  public void testInSwitchExprUp1() { bothTest(); }

  @Test
  public void testTwoStatements() { bothTest(); }

  @Test
  public void testStatementToEmptySpace() { bothTest(); }
  @Test
  public void testStatementToEmptySpace2() { bothTest(); }
  @Test
  public void testStatementToEmptySpace3() { bothTest(); }

  @Test
  public void testStatementsWithSemicolons() { bothTest(); }
  @Test
  public void testStatementsWithComments() { bothTest(); }
  @Test
  public void testMoveIntoEmptyLine() { bothTest(); }
  @Test
  public void testMoveIntoEmptyLine2() { bothTest(); }

  @Test
  public void testClassesWithFields() { bothTest(); }

  private void bothTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    final String initial = data.get(0);
    final String upper = data.get(1);
    String lower = data.size() == 2 ? data.get(0) : data.get(2);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, initial);

    GroovyEditorActionTestUtil.performAction(myFixture, IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
    myFixture.checkResult(upper);

    GroovyEditorActionTestUtil.performAction(myFixture, IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
    if (!lower.endsWith("\n") && myFixture.getEditor().getDocument().getText().endsWith("\n")) lower += "\n";
    myFixture.checkResult(lower);
  }
}

