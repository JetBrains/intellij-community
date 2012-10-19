/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.formatter

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * Test suite for static formatting. Compares two files:
 * before and after formatting
 *
 * @author Ilya.Sergey
 */
public class FormatterTest extends GroovyFormatterTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "groovy/formatter/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    groovySettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    groovySettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    groovySettings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
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
  public void testFor3() throws Throwable { doTest(); }
  public void testFor4() throws Throwable { doTest(); }
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
  public void testFields() throws Throwable { doTest(); }

  public void testClosureAfterLineComment() throws Throwable { doTest(); }
  public void testAnnotationOnSeparateLine() throws Throwable { doTest(); }
  public void testAlignMultipleVariables() throws Throwable { doTest(); }

  public void testSpockTable() throws Throwable { doTest(); }
  public void testSpockTableComments() throws Throwable { doTest(); }
  public void testSpockTableWithStringComment() throws Throwable { doTest(); }

  public void testElseIfs() throws Throwable {
    groovySettings.SPECIAL_ELSE_IF_TREATMENT = false;
    doTest();
  }

  public void testElseIfsSpecial() throws Throwable { doTest(); }
  public void testVarargDeclaration() throws Throwable { doTest(); }
  public void testPreserveSpaceBeforeClosureParameters() throws Throwable { doTest(); }
  public void testPreserveGroovydoc() throws Throwable { doTest(); }

  public void testCaseInSwitch() throws Throwable {
    groovySettings.INDENT_CASE_FROM_SWITCH = false;
    doTest();
  }
  public void testCaseInSwitchIndented() throws Throwable { doTest(); }

  public void testStuffAfterLineComments() throws Throwable { doTest(); }

  public void testAnonymousInCall() throws Throwable {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void _testLabelIndent() throws Throwable {
    groovySettings.indentOptions.LABEL_INDENT_SIZE = -2;
    doTest();
  }

  public void _testLabelIndentAbsolute() throws Throwable {
    groovySettings.indentOptions.LABEL_INDENT_ABSOLUTE = true;
    groovySettings.indentOptions.LABEL_INDENT_SIZE = 1;
    doTest();
  }

  public void testClosureParametersAligned() throws Throwable {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }
  public void testAlignClosureBraceWithCall() throws Throwable { doTest(); }
  public void testFlyingGeese() throws Throwable {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = true;
    doTest();
  }
  public void testSpaceAfterTypeCast() throws Throwable {
    groovySettings.SPACE_AFTER_TYPE_CAST = false;
    groovySettings.SPACE_WITHIN_CAST_PARENTHESES = true;
    doTest();
  }

  public void testChainCallWithClosures() throws Throwable { doTest(); }
  public void testFormatDiamonds() throws Throwable { doTest(); }
  public void testFormatAnonymousDiamonds() throws Throwable { doTest(); }
  public void testPreserveChainingLineBreaks() throws Throwable { doTest(); }
  public void testMultilineEolComment() throws Throwable { doTest(); }
  public void testRedundantClosureSpace() throws Throwable { doTest(); }
  public void testIndentNamedArguments() throws Throwable { doTest(); }
  public void testIndentAssigned() throws Throwable { doTest(); }
  public void testCommentBeforeMultilineString() throws Throwable { doTest(); }
  public void testMethodSemicolons() throws Throwable { doTest(); }

  public void testNoFlyingGeese() throws Throwable {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).SPACE_IN_NAMED_ARGUMENT = false;
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = false;
    doTest();
  }

  public void testAlignChainedCalls() throws Throwable {
    groovySettings.ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  public void testAlignBinaries() throws Throwable {
    groovySettings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
  }

  public void testAlignTernaries() throws Throwable {
    groovySettings.ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTest();
  }

  public void testAlignAssignments() throws Throwable {
    groovySettings.ALIGN_MULTILINE_ASSIGNMENT = true;
    doTest();
  }

  public void doTest() {
    final List<String> data = TestUtils.readInput(testDataPath + getTestName(true) + ".test");
    checkFormatting(data.get(0), StringUtil.trimEnd(data.get(1), "\n"));
  }

  public void testJavadocLink() throws Throwable {
    // Check that no unnecessary white spaces are introduced for the javadoc link element.
    // Check IDEA-57573 for more details.
    doTest();
  }

  public void testFieldInColumnsAlignment() {
    groovySettings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
    groovySettings.FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    groovySettings.VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    doTest();
  }
  
  public void testGeese1() {doGeeseTest();}
  public void testGeese2() {doGeeseTest();}
  public void testGeese3() {doGeeseTest();}
  public void testGeese4() {doGeeseTest();}
  public void testGeese5() {doGeeseTest();}
  public void testGeese6() {doGeeseTest();}
  public void testGeese7() {doGeeseTest();}
  public void testGeese8() {doGeeseTest();}

  public void testMapInArgumentList() {doTest();}
  public void testMapInArgList2() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).ALIGN_NAMED_ARGS_IN_MAP = true;
    doTest();
  }

  public void testForceBraces() {
    groovySettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    groovySettings.FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    groovySettings.WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    doTest();
  }

  void testNonIndentAfterClosureQualifier() { doTest() }
  void testNonIndentAfterClosureQualifier2() { doTest() }


  private void doGeeseTest() {
    GroovyCodeStyleSettings customSettings = myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class);
    boolean oldvalue = customSettings.USE_FLYING_GEESE_BRACES;
    try {
      customSettings.USE_FLYING_GEESE_BRACES = true;
      doTest();
    }
    finally {
      customSettings.USE_FLYING_GEESE_BRACES = oldvalue;
    }
  }
}
