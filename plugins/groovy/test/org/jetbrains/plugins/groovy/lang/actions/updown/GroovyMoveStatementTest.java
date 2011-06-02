/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.actions.updown;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ilyas
 */
public class GroovyMoveStatementTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/actions/moveStatement/";
  }

  public void testClazz1() throws Throwable { downTest(); }
  public void testClazz2() throws Throwable { upTest(); }

  public void testClos2() throws Throwable { upTest(); }

  public void testMeth1() throws Throwable { downTest(); }
  public void testMeth2() throws Throwable { downTest(); }
  public void testMeth3() throws Throwable { upTest(); }
  public void testMeth4() throws Throwable { upTest(); }

  public void testIfst() throws Throwable { downTest(); }
  public void testIfst2() throws Throwable { upTest(); }

  public void testSimple1() throws Throwable { downTest(); }
  public void testSimple2() throws Throwable { upTest(); }

  public void testTryst1() throws Throwable { downTest(); }
  public void testTryst2() throws Throwable { downTest(); }

  public void testStatementOutsideClosure() throws Throwable { downTest(); }
  public void testVariableOutsideClosure() throws Throwable { upTest(); }
  public void testVariableOutsideClosureDown() throws Throwable { downTest(); }
  public void testStatementInsideClosure() throws Throwable { upTest(); }

  public void testMoveGroovydocWithMethod() throws Throwable { downTest(); }
  public void testMoveMethodWithGroovydoc() throws Throwable { downTest(); }
  
  public void testMoveSecondFieldUp() throws Throwable { upTest(); }
  public void testMoveFirstFieldDown() throws Throwable { downTest(); }

  public void testVariableOverMethodInScript() throws Throwable { downTest(); }
  public void testVariableOverClassInScript() throws Throwable { downTest(); }

  public void testUpFromLastOffset() throws Throwable { upTest(); }
  
  public void testClosureWithPrequel() throws Throwable { upTest(); }

  public void testMultiLineVariable() throws Throwable { downTest(); }
  public void testClosureVariableByRBrace() throws Throwable { upTest(); }

  public void testInsideMultilineString() throws Throwable { downTest(); }
  public void _testAroundMultilineString() throws Throwable { downTest(); } //todo
  public void testAroundMultilineString2() throws Throwable { downTest(); }
  public void _testAroundMultilineStringUp() throws Throwable { upTest(); }

  public void testInSwitchCaseUp1() throws Throwable { bothTest(); }
  public void testInSwitchCaseUp2() throws Throwable { bothTest(); }
  public void testInSwitchCaseUp3() throws Throwable { bothTest(); }
  public void testInSwitchCaseUp4() throws Throwable { bothTest(); }
  public void testInSwitchCaseUp5() throws Throwable { bothTest(); }

  private void downTest() throws Exception {
    doTest(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
  }

  private void upTest() throws Exception {
    doTest(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
  }

  private void bothTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    String lower = data.get(0);
    String upper = data.get(1);
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, lower);

    performAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
    if (!upper.endsWith("\n") && myFixture.getEditor().getDocument().getText().endsWith("\n")) upper += "\n";

    myFixture.checkResult(upper);

    performAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
    if (!lower.endsWith("\n") && myFixture.getEditor().getDocument().getText().endsWith("\n")) lower += "\n";
    myFixture.checkResult(lower);
  }

  public void doTest(final String actionId) throws Exception {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));

    performAction(actionId);
    String expected = data.get(1);
    if (!expected.endsWith("\n") && myFixture.getEditor().getDocument().getText().endsWith("\n")) expected += "\n";
    myFixture.checkResult(expected);
  }

  private void performAction(String actionId) {
    myFixture.performEditorAction(actionId);
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        ((DocumentEx)myFixture.getEditor().getDocument()).stripTrailingSpaces(false);
      }
    }.execute();
  }
}

