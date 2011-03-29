package org.jetbrains.plugins.groovy.lang.surroundWith;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.06.2007
 */


public class SurroundStatementsTest extends SurroundTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/surround/statements/";
  }

  public void testClosure1() throws Exception { doTest(new SurrounderByClosure()); }
  public void testClosure2() throws Exception { doTest(new SurrounderByClosure()); }
  public void testClosure3() throws Exception { doTest(new SurrounderByClosure()); }
  public void testIf1() throws Exception { doTest(new IfSurrounder()); }
  public void testIf_else1() throws Exception { doTest(new IfElseSurrounder()); }
  public void testShouldFailWithType() throws Exception { doTest(new ShouldFailWithTypeStatementsSurrounder()); }
  public void testTry_catch1() throws Exception { doTest(new TryCatchSurrounder()); }
  public void testTry_catch_finally() throws Exception { doTest(new TryCatchFinallySurrounder()); }
  public void testTry_finally1() throws Exception { doTest(new TryFinallySurrounder()); }
  public void testWhile1() throws Exception { doTest(new WhileSurrounder()); }
  public void testWith2() throws Exception { doTest(new WithStatementsSurrounder()); }
  public void testFor1() throws Exception { doTest(new ForSurrounder()); }

}
