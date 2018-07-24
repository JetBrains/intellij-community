// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiPackage
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.util.TestUtils

import static org.jetbrains.plugins.groovy.util.TestUtils.readInput

class KeywordCompletionTest extends LightCodeInsightFixtureTestCase {

  void testBr1()                 { doTest() }
  void testCase_return()         { doTest() }
  void testClass1()              { doTest() }
  void testClass2()              { doTest() }
  void testClass3()              { doTest() }
  void testClass4()              { doTest() }
  void testExpr1()               { doTest() }
  void testExpr2()               { doTest() }
  void testFile11()              { doTest() }
  void testFile12()              { doTest() }
  void testFin()                 { doTest() }
  void testFin2()                { doTest() }
  void testGRVY1064()            { doTest() }
  void testGrvy1404()            { doTest() }
  void testImp1()                { doTest() }
  void testImp2()                { doTest() }
  void testImp3()                { doTest() }
  void testIns1()                { doTest() }
  void testIns2()                { doTest() }
  void testIns3()                { doTest() }
  void testInt1()                { doTest() }
  void testLocal1()              { doTest() }
  void testMod1()                { doTest() }
  void testMod10()               { doTest() }
  void testMod11()               { doTest() }
  void testMod2()                { doTest() }
  void testMod3()                { doTest() }
  void testMod4()                { doTest() }
  void testMod5()                { doTest() }
  void testMod6()                { doTest() }
  void testMod7()                { doTest() }
  void testMod8()                { doTest() }
  void testMod9()                { doTest() }
  void testPack1()               { doTest() }
  void testSt1()                 { doTest() }
  void testSwit1()               { doTest() }
  void testSwit13()              { doTest() }
  void testSwit14()              { doTest() }
  void testSwit2()               { doTest() }
  void testSwit3()               { doTest() }
  void testSwit4()               { doTest() }
  void testSwit5()               { doTest() }
  void testTag1()                { doTest() }
  void testTag2()                { doTest() }
  void testTag3()                { doTest() }
  void testTag4()                { doTest() }
  void testTh1()                 { doTest() }
  void testTh2()                 { doTest() }
  void testVar1()                { doTest() }
  void testVar10()               { doTest() }
  void testVar13()               { doTest() }
  void testVar2()                { doTest() }
  void testVar3()                { doTest() }
  void testVar4()                { doTest() }
  void testVar5()                { doTest() }
  void testVar6()                { doTest() }
  void testVar7()                { doTest() }
  void testVar8()                { doTest() }
  void testWhile55()             { doTest() }
  void testDefInsideCase()       { doTest() }
  void testThrows1()             { doTest() }
  void testThrows2()             { doTest() }
  void testThrows3()             { doTest() }
  void testPrimitiveTypes()      { doTest() }
  void testIncompleteConstructor() { doTest() }
  void testAtInterface()         { doTest() }
  void testInstanceOf()          { doTest() }
  void testAssert()              { doTest() }
  void testReturn()              { doTest() }
  void testAssertInClosure()     { doTest() }
  void testAfterLabel()          { doTest() }
  void testKeywordsInParentheses() { doTest() }
  void testCompletionInTupleVar(){ doTest() }
  void testAnnotationArg()       { doTest() }
  void testDefaultAnnotationArg(){ doTest() }
  void testDefaultInAnnotation() { doTest() }
  void testElse1()               { doTest() }
  void testElse2()               { doTest() }
  void testClassAfterAnnotation(){ doTest() }
  void testClassAfterAnno2()     { doTest() }
  void testExtends()             { doTest() }
  void testImplements()          { doTest() }
  void testAfterNumberLiteral()  { doTest() }

  String basePath = TestUtils.testDataPath + 'groovy/oldCompletion/keyword'

  private boolean oldAutoInsert

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    def instance = CodeInsightSettings.instance
    oldAutoInsert = instance.AUTOCOMPLETE_ON_CODE_COMPLETION
    instance.AUTOCOMPLETE_ON_CODE_COMPLETION = false
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.instance.AUTOCOMPLETE_ON_CODE_COMPLETION = oldAutoInsert
    super.tearDown()
  }

  protected void doTest() {
    final testName = getTestName(true)
    def fileName = "${testName}.test"
    final def (input) = readInput("$testDataPath/$fileName")
    myFixture.configureByText "${testName}.groovy", input
    def actual = myFixture.completeBasic().findAll {
      def o = it.object
      !(o instanceof PsiMember) &&
      !(o instanceof GrVariable) &&
      !(o instanceof GroovyResolveResult) &&
      !(o instanceof PsiPackage)
    } collect {
      it.lookupString
    } sort() join("\n")

    myFixture.configureByText "actual.txt", """\
${input}
-----
${actual}\
"""
    myFixture.checkResultByFile fileName
  }
}
