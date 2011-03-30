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

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/surround/expr/";
  }

}
