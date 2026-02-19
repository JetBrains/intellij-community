// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import org.jetbrains.plugins.groovy.util.TestUtils;

public class SurroundStatementsTest extends SurroundTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/surround/statements/";
  }

  public void testClosure1() { doTest(new SurrounderByClosure()); }

  public void testClosure2() { doTest(new SurrounderByClosure()); }

  public void testClosure3() { doTest(new SurrounderByClosure()); }

  public void testIf1() { doTest(new IfSurrounder()); }

  public void testIf_else1() { doTest(new IfElseSurrounder()); }

  public void testShouldFailWithType() { doTest(new ShouldFailWithTypeStatementsSurrounder()); }

  public void testTry_catch1() { doTest(new TryCatchSurrounder()); }

  public void testTry_catch_finally() { doTest(new TryCatchFinallySurrounder()); }

  public void testTry_finally1() { doTest(new TryFinallySurrounder()); }

  public void testTry_finallyFormatting() { doTest(new TryFinallySurrounder()); }

  public void testWhile1() { doTest(new WhileSurrounder()); }

  public void testWith2() { doTest(new WithStatementsSurrounder()); }

  public void testFor1() { doTest(new ForSurrounder()); }

  public void testIfComments() { doTest(new IfSurrounder()); }

  public void testBracesInIf() {
    doTest(new GrBracesSurrounder(), """
      if (abc)
          pr<caret>int 'abc'
      """, """
             if (abc) {
                 print 'abc'
             }
             """);
  }

  public void testBracesInWhile() {
    doTest(new GrBracesSurrounder(), """
      while (true)
          print 'en<caret>dless'
      """, """
             while (true) {
                 print 'endless'
             }
             """);
  }

  public void testBraces() {
    doTest(new GrBracesSurrounder(), """
      print 2
      pri<caret>nt 3
      print 4
      """, """
             print 2
             { print 3 }
             print 4
             """);
  }
}
