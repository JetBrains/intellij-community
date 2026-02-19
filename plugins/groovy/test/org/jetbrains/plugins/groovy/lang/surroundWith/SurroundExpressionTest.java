// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import org.jetbrains.plugins.groovy.util.TestUtils;

public class SurroundExpressionTest extends SurroundTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/surround/expr/";
  }

  public void testBrackets1() { doTest(new ParenthesisExprSurrounder()); }

  public void testIf1() { doTest(new IfExprSurrounder()); }

  public void testIf_else1() { doTest(new IfElseExprSurrounder()); }

  public void testType_cast1() { doTest(new TypeCastSurrounder()); }

  public void testType_cast2() { doTest(new TypeCastSurrounder()); }

  public void testWhile1() { doTest(new WhileExprSurrounder()); }

  public void testWith2() { doTest(new WithExprSurrounder()); }

  public void testBinaryWithCast() { doTest(new TypeCastSurrounder()); }

  public void testCommandArgList() { doTest(new TypeCastSurrounder()); }

  public void testCommandArgList2() { doTest(new TypeCastSurrounder()); }

  public void testCommandArgList3() { doTest(new TypeCastSurrounder()); }

  public void testCommandArgList4() { doTest(new TypeCastSurrounder()); }

  public void testNotAndParenthesesSurrounder() { doTest(new NotAndParenthesesSurrounder()); }

  public void testNotAndParenthesesOnCommandExpr() { doTest(new NotAndParenthesesSurrounder()); }
}
