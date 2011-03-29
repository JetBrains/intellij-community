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

  public void testClosure1() throws Exception { doTest(new GroovySurrounderByClosure()); }
  public void testClosure2() throws Exception { doTest(new GroovySurrounderByClosure()); }
  public void testClosure3() throws Exception { doTest(new GroovySurrounderByClosure()); }
  public void testIf1() throws Exception { doTest(new GroovyWithIfSurrounder()); }
  public void testIf_else1() throws Exception { doTest(new GroovyWithIfElseSurrounder()); }
  public void testShouldFailWithType() throws Exception { doTest(new GroovyWithShouldFailWithTypeStatementsSurrounder()); }
  public void testTry_catch1() throws Exception { doTest(new GroovyWithTryCatchSurrounder()); }
  public void testTry_catch_finally() throws Exception { doTest(new GroovyWithTryCatchFinallySurrounder()); }
  public void testTry_finally1() throws Exception { doTest(new GroovyWithTryFinallySurrounder()); }
  public void testWhile1() throws Exception { doTest(new GroovyWithWhileSurrounder()); }
  public void testWith2() throws Exception { doTest(new GroovyWithWithStatementsSurrounder()); }
  public void testFor1() throws Exception { doTest(new GroovyWithForSurrounder()); }

}
