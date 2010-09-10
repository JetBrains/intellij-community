/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * Test suite for static formatting. Compares two files:
 * before and after formatting
 *
 * @author Ilya.Sergey
 */
public class FormatterTest extends GroovyFormatterTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/formatter/";
  }

  protected void setUp() throws Exception {
    super.setUp();
    myTempSettings.CLASS_BRACE_STYLE = CodeStyleSettings.END_OF_LINE;
    myTempSettings.METHOD_BRACE_STYLE = CodeStyleSettings.END_OF_LINE;
    myTempSettings.BRACE_STYLE = CodeStyleSettings.END_OF_LINE;
  }

  public void testAddign1() throws Throwable { doTest(); }
  public void testArg1() throws Throwable { doTest(); }
  public void testArg2() throws Throwable { doTest(); }
  public void testBin1() throws Throwable { doTest(); }
  public void testBin2() throws Throwable { doTest(); }
  public void testBlockExpr1() throws Throwable { doTest(); }
  public void testClass1() throws Throwable { doTest(); }
  public void testClo1() throws Throwable { doTest(); }
  public void testClo2() throws Throwable { doTest(); }
  public void testClo3() throws Throwable { doTest(); }
  public void testClo4() throws Throwable { doTest(); }
  public void testColon1() throws Throwable { doTest(); }
  public void testColon2() throws Throwable { doTest(); }
  public void testCond1() throws Throwable { doTest(); }
  public void testDoc1() throws Throwable { doTest(); }
  public void testDoc2() throws Throwable { doTest(); }
  public void testDoc3() throws Throwable { doTest(); }
  public void testDockter() throws Throwable { doTest(); }
  public void testDot1() throws Throwable { doTest(); }
  public void testDot2() throws Throwable { doTest(); }
  public void testFor1() throws Throwable { doTest(); }
  public void testFor2() throws Throwable { doTest(); }
  public void testGbegin1() throws Throwable { doTest(); }
  public void testGrvy1637() throws Throwable { doTest(); }
  public void testGString1() throws Throwable { doTest(); }
  public void testMap6() throws Throwable { doTest(); }
  public void testMeth1() throws Throwable { doTest(); }
  public void testMeth2() throws Throwable { doTest(); }
  public void testMeth3() throws Throwable { doTest(); }
  public void testMeth4() throws Throwable { doTest(); }
  public void testMeth5() throws Throwable { doTest(); }
  public void testMultistring1() throws Throwable { doTest(); }
  public void testMultistring2() throws Throwable { doTest(); }
  public void testNew1() throws Throwable { doTest(); }
  public void testParam1() throws Throwable { doTest(); }
  public void testParam2() throws Throwable { doTest(); }
  public void testParen1() throws Throwable { doTest(); }
  public void testPath1() throws Throwable { doTest(); }
  public void testPointer1() throws Throwable { doTest(); }
  public void testRange1() throws Throwable { doTest(); }
  public void testRegex1() throws Throwable { doTest(); }
  public void testSh1() throws Throwable { doTest(); }
  public void testSh2() throws Throwable { doTest(); }
  public void testSqr1() throws Throwable { doTest(); }
  public void testSqr2() throws Throwable { doTest(); }
  public void testSqr3() throws Throwable { doTest(); }
  public void testString1() throws Throwable { doTest(); }
  public void testSuper1() throws Throwable { doTest(); }
  public void testSwitch1() throws Throwable { doTest(); }
  public void testSwitch2() throws Throwable { doTest(); }
  public void testSwitch3() throws Throwable { doTest(); }
  public void testSwitch4() throws Throwable { doTest(); }
  public void testSwitch5() throws Throwable { doTest(); }
  public void testSwitch6() throws Throwable { doTest(); }
  public void testSwitch7() throws Throwable { doTest(); }
  public void testSwitch8() throws Throwable { doTest(); }
  public void testType1() throws Throwable { doTest(); }
  public void testTypeparam1() throws Throwable { doTest(); }
  public void testUn1() throws Throwable { doTest(); }
  public void testUn2() throws Throwable { doTest(); }
  public void testUn3() throws Throwable { doTest(); }
  public void testWhile1() throws Throwable { doTest(); }

  public void testWhile2() throws Throwable { doTest(); }

  public void testWhileCStyle() throws Throwable { doTest(); }

  public void testClosureAfterLineComment() throws Throwable { doTest(); }
  public void testAnnotationOnSeparateLine() throws Throwable { doTest(); }

  public void testElseIfs() throws Throwable {
    myTempSettings.SPECIAL_ELSE_IF_TREATMENT = false;
    doTest();
  }

  public void testElseIfsSpecial() throws Throwable { doTest(); }
  public void testVarargDeclaration() throws Throwable { doTest(); }
  public void testPreserveSpaceBeforeClosureParameters() throws Throwable { doTest(); }
  
  public void testCaseInSwitch() throws Throwable {
    myTempSettings.INDENT_CASE_FROM_SWITCH = false;
    doTest();
  }
  public void testCaseInSwitchIndented() throws Throwable { doTest(); }

  public void testClosureParametersAligned() throws Throwable {
    myTempSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void doTest() throws Throwable {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    checkFormatting(data.get(0), data.get(1));
  }

  public void testJavadocLink() throws Throwable {
    // Check that no unnecessary white spaces are introduced for the javadoc link element.
    // Check IDEA-57573 for more details.
    doTest();
  }
}
