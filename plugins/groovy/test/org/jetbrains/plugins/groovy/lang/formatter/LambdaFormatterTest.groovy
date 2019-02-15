// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.formatter

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils

class LambdaFormatterTest extends GroovyFormatterTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/formatter/lambda"

  void testBraceStyle1() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    doTest()
  }

  void testBraceStyle2() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
  }

  void testBraceStyle3() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    doTest()
  }

  void testBraceStyle4() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    doTest()
  }

  void testBraceStyle5() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    doTest()
  }

  void testBraceStyle6() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    doTest()
  }

  void testBraceStyle7() {
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true
    doTest()
  }

  void testBraceStyle8() {
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    groovySettings.SPACE_WITHIN_BRACES = false
    doTest()
  }

  void testOneLineLambda1() {
    doTest()
  }

  void testOneLineLambda2() {
    doTest()
  }

  void testOneLineLambda3() {
    doTest()
  }

  void testOneLineLambda4() {
    doTest()
  }

  void testOneLineLambda5() {
    doTest()
  }

  void testOneLineLambda6() {
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    doTest()
  }

  void testOneLineLambda7() {
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
  }

  void testParams() { doTest() }

  void testLambdaParametersAligned() {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest()
  }

  void testAlignLambdaBraceWithCall() { doTest() }

  void testChainCall1() { doTest() }

  void testChainCall2() { doTest() }

  //TODO: IDEA-207367
  void _testChainCall3() { doTest() }

  //TODO: IDEA-207367
  void _testChainCall4() { doTest() }

  void testChainCall5() { doTest() }

  void testChainCall6() {
    doTest()
  }

  void testChainCall7() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    doTest()
  }

  void testChainCallWithSingleExpressionLambda1() {
    doTest()
  }

  void testChainCallWithSingleExpressionLambda2() {
    getGroovySettings().KEEP_LINE_BREAKS = false
    doTest()
  }

  void testNoFlyingGeese() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).SPACE_IN_NAMED_ARGUMENT = false
    doTest()
  }

  void testNoFlyingGeese2() {
    getGroovySettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
  }

  void testSpacesAroundArrow1() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    doTest()
  }

  void testSpacesAroundArrow2() {
    doTest()
  }

  void testSpacesAroundArrow3() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    getGroovySettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    doTest()
  }

  void testSpacesAroundArrow4() {
    getGroovySettings().KEEP_LINE_BREAKS = false
    doTest()
  }

  void testSpacesAroundArrow5() {
    doTest()
  }

  void testSpacesAroundArrow6() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    doTest()
  }

  void testSpacesAroundArrow7() {
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    doTest()
  }

  void testSpacesAroundArrow8() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    groovySettings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    doTest()
  }

  void testSpacesAroundArrow9() {
    getGroovySettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    doTest()
  }

  void testSpacesAroundArrow10() {
    groovySettings.SPACE_AROUND_LAMBDA_ARROW = false
    doTest()
  }

  void doTest() {
    doTest(getTestName(true).replace('$', '/') + ".test")
  }

  protected void doTest(String fileName) {
    String path = testDataPath + "/" + fileName
    def (String input) = TestUtils.readInput(path)
    checkParsing(input, fileName)
  }

  protected void checkParsing(String input, String path) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, input)
    doFormat(myFixture.getFile())
    final String prefix = input + '\n-----\n'
    myFixture.configureByText('test.txt', prefix + myFixture.getFile().getText())
    myFixture.checkResultByFile(path, false)
  }
}
