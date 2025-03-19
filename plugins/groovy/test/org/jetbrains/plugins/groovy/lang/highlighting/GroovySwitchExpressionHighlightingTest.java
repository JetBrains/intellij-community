// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrSwitchExhaustivenessCheckInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

public class GroovySwitchExpressionHighlightingTest extends LightGroovyTestCase implements HighlightingTest {
  public void doTest(String text) {
    myFixture.configureByText("_.groovy", text);
    myFixture.doHighlighting();
    myFixture.checkResult(text);
  }

  public void testNoMixingArrowsAndColons() {
    doTest("""
             def x = switch (10) {
               case 20 <error>-></error> 10
               case 50<error>:</error>
                 yield 5
             }""");
  }

  public void testRequireYieldInColonStyleSwitchExpression() {
    doTest("""
             def x = switch (10) {
               <error>case</error> 20:
                 40
             }""");
  }

  public void testForbidReturnInColonStyleSwitchExpression() {
    doTest("""
             def x = switch (10) {
               <error>case</error> 20:
                 <error>return 40</error>
             }""");
  }

  public void testThrowInColonStyleSwitchExpression() {
    doTest("""
             def x = switch (10) {
                 case 20:\s
                     throw new IOException()
             }""");
  }

  public void testEmptySwitch() {
    doTest("""
             def x = <error>switch</error> (10) {}""");
  }

  public void testCheckBooleanExhaustiveness() {
    highlightingTest("""
                       def foo(boolean b) {
                         def x = switch (b) {
                           case true -> 1
                           case false -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);

    highlightingTest("""
                       def foo(boolean b) {
                         def x = <weak_warning>switch</weak_warning> (b) {
                           case false -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);

    highlightingTest("""
                       def foo(boolean b) {
                         def x = <weak_warning>switch</weak_warning> (b) {
                           case true -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);

    highlightingTest("""
                       def foo(boolean b) {
                         def x = switch (b) {
                           default -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testNumericalRangeExhaustiveness() {
    highlightingTest("""
                       def foo(byte b) {
                         def x = switch (b) {
                           case -128..<0 -> 1
                           case 0..127 -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);

    highlightingTest("""
                       def foo(byte b) {
                         def x = <weak_warning>switch</weak_warning> (b) {
                           case -128..<0 -> 1
                           case 0..126 -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testIncompleteEnum() {
    highlightingTest("""
                       enum A { X, Y }

                       def foo(A b) {
                         def x = <weak_warning>switch</weak_warning> (b) {
                           case A.X -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testCompleteEnum() {
    highlightingTest("""
                       enum A { X, Y }

                       def foo(A b) {
                         def x = switch (b) {
                           case A.X -> 1
                           case A.Y -> 2
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testIncompleteSealedClass() {
    highlightingTest("""
                       sealed class A {}
                       class B extends A {}
                       class C extends A {}

                       def foo(A b) {
                         def x = <weak_warning>switch</weak_warning> (b) {
                           case B -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testCompleteSealedClass() {
    highlightingTest("""
                       abstract sealed class A {}
                       class B extends A {}
                       class C extends A {}

                       def foo(A b) {
                         def x = switch (b) {
                           case B -> 1
                           case C -> 2
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testCompleteMatchingOnTypes() {
    highlightingTest("""
                       def foo(IOException b) {
                         def x = switch (b) {
                           case Throwable -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testUntypedCondition() {
    highlightingTest("""
                       def foo(b) {
                         def x = <weak_warning>switch</weak_warning> (b) {
                           case 10 -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testPlainSwitchStatementIsNotHighligted() {
    highlightingTest("""
                       void main(String[] args) {
                              switch (10) {
                                  case 20:
                                      return
                              }
                           }""");
  }

  public void testUnmatchedNull() {
    GrSwitchExhaustivenessCheckInspection inspection = new GrSwitchExhaustivenessCheckInspection();
    inspection.enableNullCheck();
    highlightingTest("""
                       enum A { B }
                       def foo(A b) {
                         def x = <weak_warning>switch</weak_warning> (b) {
                           case A.B -> 1
                         }
                       }
                       """, inspection);
  }

  public void testExplicitlyMatchedNull() {
    GrSwitchExhaustivenessCheckInspection inspection = new GrSwitchExhaustivenessCheckInspection();
    inspection.enableNullCheck();
    highlightingTest("""
                       enum A { B }
                       def foo(A b) {
                         def x = switch (b) {
                           case A.B -> 1
                           case null -> 2
                         }
                       }
                       """, inspection);
  }

  public void testHighlightWithOnlyOneSubclassMatched() {
    highlightingTest("""
                       class A {

                       }

                       class B extends A {}


                       def foo(A a) {
                           def x = <weak_warning>switch</weak_warning> (a) {
                               case B -> 30
                           }
                       }""", GrSwitchExhaustivenessCheckInspection.class);
  }

  public void testNestedSealed() {
    highlightingTest("""
                       abstract sealed class A {}
                       class B extends A {}
                       abstract sealed class C extends A {}
                       class D extends C {}
                       class E extends C {}

                       def foo(A a) {
                         def x = switch (a) {
                           case B -> 30
                           case D -> 20
                           case E -> 40
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_4_0;
  }
}
