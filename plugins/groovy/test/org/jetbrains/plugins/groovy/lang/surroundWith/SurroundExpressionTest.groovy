/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.surroundWith;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.06.2007
 */
public class SurroundExpressionTest extends SurroundTestCase {

  public void testBrackets1() throws Exception { doTest(new ParenthesisExprSurrounder()); }
  public void testIf1() throws Exception { doTest(new IfExprSurrounder()); }
  public void testIf_else1() throws Exception { doTest(new IfElseExprSurrounder()); }
  public void testType_cast1() throws Exception { doTest(new TypeCastSurrounder()); }
  public void testType_cast2() throws Exception { doTest(new TypeCastSurrounder()); }
  public void testWhile1() throws Exception { doTest(new WhileExprSurrounder()); }
  public void testWith2() throws Exception { doTest(new WithExprSurrounder()); }
  public void testBinaryWithCast() throws Exception { doTest(new TypeCastSurrounder()); }
  public void testCommandArgList() { doTest(new TypeCastSurrounder()) }
  public void testCommandArgList2() { doTest(new TypeCastSurrounder()) }
  public void testCommandArgList3() { doTest(new TypeCastSurrounder()) }
  public void testCommandArgList4() { doTest(new TypeCastSurrounder()) }
  public void testNotAndParenthesesSurrounder() { doTest(new NotAndParenthesesSurrounder()) }
  public void testNotAndParenthesesOnCommandExpr() { doTest(new NotAndParenthesesSurrounder()) }

  final String basePath = TestUtils.testDataPath + "groovy/surround/expr/"
}
