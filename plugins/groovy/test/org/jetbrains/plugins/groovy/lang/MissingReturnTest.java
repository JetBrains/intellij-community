// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class MissingReturnTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/missingReturn";
  }

  public void testMissingReturnWithLastLoop() { doTest(); }

  public void testMissingReturnWithUnknownCall() { doTest(); }

  public void testMissingReturnWithIf() { doTest(); }

  public void testMissingReturnWithAssertion() { doTest(); }

  public void testMissingReturnThrowException() { doTest(); }

  public void testMissingReturnTryCatch() { doTest(); }

  public void testMissingReturnLastNull() { doTest(); }

  public void testMissingReturnImplicitReturns() { doTest(); }

  public void testMissingReturnOvertReturnType() { doTest(); }

  public void testMissingReturnFromClosure() { doTest(); }

  public void testReturnsWithoutValue() { doTest(); }

  public void testEndlessLoop() { doTest(); }

  public void testEndlessLoop2() { doTest(); }

  public void testExceptionWithFinally() { doTest(); }

  public void testOnlyAssert() { doTest(); }

  public void testImplicitReturnNull() { doTest(); }

  public void testMissingReturnInClosure() { doTest(); }

  public void testFinally() { doTest(); }

  public void testClosureWithExplicitExpectedType() { doTest(); }

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

  public void testReturnWithoutValue1() {
    doTextText("""
                 int foo() {
                   return
                 <warning>}</warning>
                 """);
  }

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

  public void testReturnWithoutValue3() {
    doTextText("""
                 void foo() {
                   return
                 } //no error
                 """);
  }

  public void testSingleThrow1() {
    doTextText("""
                 int foo() {
                   throw new RuntimeException()
                 } //correct
                 """);
  }

  public void testSingleThrow2() {
    doTextText("""
                 void foo() {
                   throw new RuntimeException()
                 } //correct
                 """);
  }

  public void testSingleThrow3() {
    doTextText("""
                 def foo() {
                   throw new RuntimeException()
                 } //correct
                 """);
  }

  public void testThrowFromIf1() {
    doTextText("""
                 int foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                 <warning>}</warning>
                 """);
  }

  public void testThrowFromIf2() {
    doTextText("""
                 void foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                 } //correct
                 """);
  }

  public void testThrowFromIf3() {
    doTextText("""
                 def foo() {
                   if (1) {
                     throw new RuntimeException()
                   }
                 } //correct
                 """);
  }

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
    myFixture.configureByText("___.groovy", text);
    myFixture.enableInspections(MissingReturnInspection.class);
    myFixture.testHighlighting(true, false, false);
  }

  private void doTest() {
    myFixture.enableInspections(new MissingReturnInspection());
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy");
  }
}
