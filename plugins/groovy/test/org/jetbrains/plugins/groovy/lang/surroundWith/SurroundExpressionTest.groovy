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
package org.jetbrains.plugins.groovy.lang.surroundWith

import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * User: Dmitry.Krasilschikov
 */
class SurroundExpressionTest extends SurroundTestCase {

  void testBrackets1() throws Exception { doTest(new ParenthesisExprSurrounder()) }

  void testIf1() throws Exception { doTest(new IfExprSurrounder()) }

  void testIf_else1() throws Exception { doTest(new IfElseExprSurrounder()) }

  void testType_cast1() throws Exception { doTest(new TypeCastSurrounder()) }

  void testType_cast2() throws Exception { doTest(new TypeCastSurrounder()) }

  void testWhile1() throws Exception { doTest(new WhileExprSurrounder()) }

  void testWith2() throws Exception { doTest(new WithExprSurrounder()) }

  void testBinaryWithCast() throws Exception { doTest(new TypeCastSurrounder()) }

  void testCommandArgList() { doTest(new TypeCastSurrounder()) }

  void testCommandArgList2() { doTest(new TypeCastSurrounder()) }

  void testCommandArgList3() { doTest(new TypeCastSurrounder()) }

  void testCommandArgList4() { doTest(new TypeCastSurrounder()) }

  void testNotAndParenthesesSurrounder() { doTest(new NotAndParenthesesSurrounder()) }

  void testNotAndParenthesesOnCommandExpr() { doTest(new NotAndParenthesesSurrounder()) }

  final String basePath = TestUtils.testDataPath + "groovy/surround/expr/"
}
