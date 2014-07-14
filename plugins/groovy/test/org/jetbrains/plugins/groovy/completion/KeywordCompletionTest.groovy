/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.completion

import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ilyas
 */
public class KeywordCompletionTest extends CompletionTestBase {

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
  void _testImp4()                { doTest() }
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

  @Override
  protected boolean addReferenceVariants() { false }

}
