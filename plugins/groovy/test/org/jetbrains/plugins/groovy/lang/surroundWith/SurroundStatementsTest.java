// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class SurroundStatementsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/surround/statements/");

  @Test
  public void testClosure1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new SurrounderByClosure()); }

  @Test
  public void testClosure2() { SurroundTestUtil.doTest(codeInsightFixture.get(), new SurrounderByClosure()); }

  @Test
  public void testClosure3() { SurroundTestUtil.doTest(codeInsightFixture.get(), new SurrounderByClosure()); }

  @Test
  public void testIf1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new IfSurrounder()); }

  @Test
  public void testIf_else1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new IfElseSurrounder()); }

  @Test
  public void testShouldFailWithType() { SurroundTestUtil.doTest(codeInsightFixture.get(), new ShouldFailWithTypeStatementsSurrounder()); }

  @Test
  public void testTry_catch1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TryCatchSurrounder()); }

  @Test
  public void testTry_catch_finally() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TryCatchFinallySurrounder()); }

  @Test
  public void testTry_finally1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TryFinallySurrounder()); }

  @Test
  public void testTry_finallyFormatting() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TryFinallySurrounder()); }

  @Test
  public void testWhile1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new WhileSurrounder()); }

  @Test
  public void testWith2() { SurroundTestUtil.doTest(codeInsightFixture.get(), new WithStatementsSurrounder()); }

  @Test
  public void testFor1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new ForSurrounder()); }

  @Test
  public void testIfComments() { SurroundTestUtil.doTest(codeInsightFixture.get(), new IfSurrounder()); }

  @Test
  public void testBracesInIf() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    SurroundTestUtil.doTest(myFixture, new GrBracesSurrounder(), """
      if (abc)
          pr<caret>int 'abc'
      """, """
             if (abc) {
                 print 'abc'
             }
             """);
  }

  @Test
  public void testBracesInWhile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    SurroundTestUtil.doTest(myFixture, new GrBracesSurrounder(), """
      while (true)
          print 'en<caret>dless'
      """, """
             while (true) {
                 print 'endless'
             }
             """);
  }

  @Test
  public void testBraces() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    SurroundTestUtil.doTest(myFixture, new GrBracesSurrounder(), """
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
