// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class MissingReturnTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "highlighting/missingReturn");

  @Test
  public void testMissingReturnWithLastLoop() { doTest(); }

  @Test
  public void testMissingReturnWithUnknownCall() { doTest(); }

  @Test
  public void testMissingReturnWithIf() { doTest(); }

  @Test
  public void testMissingReturnWithAssertion() { doTest(); }

  @Test
  public void testMissingReturnThrowException() { doTest(); }

  @Test
  public void testMissingReturnTryCatch() { doTest(); }

  @Test
  public void testMissingReturnLastNull() { doTest(); }

  @Test
  public void testMissingReturnImplicitReturns() { doTest(); }

  @Test
  public void testMissingReturnOvertReturnType() { doTest(); }

  @Test
  public void testMissingReturnFromClosure() { doTest(); }

  @Test
  public void testReturnsWithoutValue() { doTest(); }

  @Test
  public void testEndlessLoop() { doTest(); }

  @Test
  public void testEndlessLoop2() { doTest(); }

  @Test
  public void testExceptionWithFinally() { doTest(); }

  @Test
  public void testOnlyAssert() { doTest(); }

  @Test
  public void testImplicitReturnNull() { doTest(); }

  @Test
  public void testMissingReturnInClosure() { doTest(); }

  @Test
  public void testFinally() { doTest(); }

  @Test
  public void testClosureWithExplicitExpectedType() { doTest(); }

  @Test
  public void testAssert() {
    doTextText("""
                 Integer.with {
                   assert valueof('1') == 1
                 }                         //no error

                 Integer.with {
                   if (foo) {
                     return 2
                   }
                   else {
                     print 1
                   }
                 <warning descr="Not all execution paths return a value">}</warning>
                 """);
  }

  @Test
  public void testInterruptFlowInElseBranch() {
    doTextText("""
                 //correct
                 public int foo(int bar) {
                     if (bar < 0) {
                         return -1
                     }
                     else if (bar > 0) {
                         return 12
                     }
                     else {
                         throw new IllegalArgumentException('bar cannot be zero!')
                     }
                 }

                 //incorrect
                 public int foo2(int bar) {
                     if (bar < 0) {
                         return -1
                     }
                     else if (bar > 0) {
                         return 12
                     }
                 <warning descr="Not all execution paths return a value">}</warning>
                 """);
  }

  @Test
  public void testSwitch() {
    doTextText("""
                 //correct
                 String foo(e) {
                     switch(e) {
                         case 1: 1; break
                         case 2: return 2; break
                         default: 3
                     }
                 }

                 //incorrect
                 String foo2(e) {
                     switch(e) {
                         case 1: 1; break
                         case 2: break
                         default: 3
                     }
                 <warning descr="Not all execution paths return a value">}</warning>
                 """);
  }

  @Test
  public void testSwitchWithIf() {
    doTextText("""
                 //correct
                 String foo(e) {
                     switch(e) {
                         case 1:
                             if (e=='a') {
                                 return 'a'
                             }
                             else {
                                 'c'
                             }
                             break
                         default: ''
                     }
                 }

                 //incorrect
                 String foo2(e) {
                     switch(e) {
                         case 1:
                             if (e=='a') {
                                 return 'a'
                             }
                             else {
                            //     'c'
                             }
                             break
                         default: ''
                     }
                 <warning descr="Not all execution paths return a value">}</warning>
                 """);
  }

  @Test
  public void testConditional() {
    doTextText("""
                 //correct
                 List createFilters1() {
                     abc ? [1] : []
                 }

                 //correct
                 List createFilters2() {
                     abc ?: [1]
                 }

                 //incorrect
                 List createFilters3() {
                 <warning descr="Not all execution paths return a value">}</warning>
                 """);
  }

  @Test
  public void testReturnWithoutValue0() {
    doTextText("""
                 int foo() {
                   if (abc) {
                     return
                   }

                   return 2
                 <warning>}</warning>
                 """);
  }

  @Test
  public void testReturnWithoutValue1() {
    doTextText("""
                 int foo() {
                   return
                 <warning>}</warning>
                 """);
  }

  @Test
  public void testReturnWithoutValue2() {
    doTextText("""
                 void foo() {
                   if (abc) {
                     return
                   }

                   print 2
                 } //no error
                 """);
  }

  @Test
  public void testReturnWithoutValue3() {
    doTextText("""
                 void foo() {
                   return
                 } //no error
                 """);
  }

  @Test
  public void testSingleThrow1() {
    doTextText("""
                 int foo() {
                   throw new RuntimeException()
                 } //correct
                 """);
  }

  @Test
  public void testSingleThrow2() {
    doTextText("""
                 void foo() {
                   throw new RuntimeException()
                 } //correct
                 """);
  }

  @Test
  public void testSingleThrow3() {
    doTextText("""
                 def foo() {
                   throw new RuntimeException()
                 } //correct
                 """);
  }

  @Test
  public void testThrowFromIf1() {
    doTextText("""
                 int foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                 <warning>}</warning>
                 """);
  }

  @Test
  public void testThrowFromIf2() {
    doTextText("""
                 void foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                 } //correct
                 """);
  }

  @Test
  public void testThrowFromIf3() {
    doTextText("""
                 def foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                 } //correct
                 """);
  }

  @Test
  public void testThrowFromIf4() {
    doTextText("""
                 int foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                   else {
                     return 1
                   }
                 }
                 """);
  }

  @Test
  public void testThrowFromIf5() {
    doTextText("""
                 void foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                   else {
                     throw new RuntimeException()
                   }
                 } //correct
                 """);
  }

  @Test
  public void testThrowFromIf6() {
    doTextText("""
                 def foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                   else {
                     return 1
                   }
                 } //correct
                 """);
  }

  public void doTextText(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("___.groovy", text);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.enableInspections(MissingReturnInspection.class);
      myFixture.testHighlighting(true, false, false);
    });
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new MissingReturnInspection());
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(false) + ".groovy");
  }
}
