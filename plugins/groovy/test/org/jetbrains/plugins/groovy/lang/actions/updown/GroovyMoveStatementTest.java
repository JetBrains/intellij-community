/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
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
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.actions.GroovyEditorActionTestBase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ilyas
 */
public class GroovyMoveStatementTest extends GroovyEditorActionTestBase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/actions/moveStatement/";
  }

  public void testClazz2() { bothTest(); }

  public void testClos2() { bothTest(); }

  public void testMeth1() { bothTest(); }

  public void testMeth2() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));
    performAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
    myFixture.checkResult(data.get(1));
  }

  public void testMeth3() { bothTest(); }
  public void testMeth4() { bothTest(); }

  public void testIfst() { bothTest(); }
  public void testIfst2() { bothTest(); }

  public void testSimple1() { bothTest(); }
  public void testSimple2() { bothTest(); }

  public void testTryst1() { bothTest(); }
  public void testTryst2() { bothTest(); }

  public void testStatementOutsideClosure() { bothTest(); }
  public void testVariableOutsideClosure() { bothTest(); }
  public void testVariableOutsideClosureDown() { bothTest(); }
  public void testStatementInsideClosure() { bothTest(); }

  public void testMoveGroovydocWithMethod() { bothTest(); }
  public void testLeaveGroovydocWithMethod() { bothTest(); }
  public void testMoveMethodWithGroovydoc() { bothTest(); }
  public void testMoveGroovyDocWithField() { bothTest(); }
  
  public void testMoveSecondFieldUp() { bothTest(); }
  public void testMoveFirstFieldDown() { bothTest(); }

  public void testVariableOverMethodInScript() { bothTest(); }
  public void testVariableOverClassInScript() { bothTest(); }

  public void testUpFromLastOffset() { bothTest(); }
  
  public void testClosureWithPrequel() { bothTest(); }

  public void testMultiLineVariable() { bothTest(); }
  public void testClosureVariableByRBrace() { bothTest(); }

  public void testInsideMultilineString() { bothTest(); }
  public void testAroundMultilineString() { bothTest(); }
  public void testAroundMultilineString2() { bothTest(); }

  public void testInSwitchCaseUp1() { bothTest(); }
  public void testInSwitchCaseUp2() { bothTest(); }
  public void testInSwitchCaseUp3() { bothTest(); }
  public void testInSwitchCaseUp4() { bothTest(); }
  public void testInSwitchCaseUp5() { bothTest(); }

  public void testTwoStatements() { bothTest(); }

  public void testStatementToEmptySpace() { bothTest(); }
  public void testStatementToEmptySpace2() { bothTest(); }
  public void testStatementToEmptySpace3() { bothTest(); }

  public void testStatementsWithSemicolons() { bothTest(); }
  public void testStatementsWithComments() { bothTest(); }
  public void testMoveIntoEmptyLine() { bothTest(); }
  public void testMoveIntoEmptyLine2() { bothTest(); }

  public void testClassesWithFields() { bothTest(); }

  private void bothTest() {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    final String initial = data.get(0);
    final String upper = data.get(1);
    String lower = data.size() == 2 ? data.get(0) : data.get(2);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, initial);

    performAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
    myFixture.checkResult(upper);

    performAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
    if (!lower.endsWith("\n") && myFixture.getEditor().getDocument().getText().endsWith("\n")) lower += "\n";
    myFixture.checkResult(lower);
  }
}

