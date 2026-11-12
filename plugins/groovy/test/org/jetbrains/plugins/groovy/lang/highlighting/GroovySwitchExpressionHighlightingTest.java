// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrSwitchExhaustivenessCheckInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovySwitchExpressionHighlightingTest implements HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Override
  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public String getTestName() {
    return LightGroovyFixtures.groovyTestName(codeInsightFixture.get());
  }

  public void doTest(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", text);
    myFixture.doHighlighting();
    myFixture.checkResult(text);
  }

  @Test
  public void testNoMixingArrowsAndColons() {
    doTest("""
             def x = switch (10) {
               case 20 <error>-></error> 10
               case 50<error>:</error>
                 yield 5
             }""");
  }

  @Test
  public void testRequireYieldInColonStyleSwitchExpression() {
    doTest("""
             def x = switch (10) {
               <error>case</error> 20:
                 40
             }""");
  }

  @Test
  public void testForbidReturnInColonStyleSwitchExpression() {
    doTest("""
             def x = switch (10) {
               <error>case</error> 20:
                 <error>return 40</error>
             }""");
  }

  @Test
  public void testThrowInColonStyleSwitchExpression() {
    doTest("""
             def x = switch (10) {
                 case 20:\s
                     throw new IOException()
             }""");
  }

  @Test
  public void testEmptySwitch() {
    doTest("""
             def x = <error>switch</error> (10) {}""");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testCompleteMatchingOnTypes() {
    highlightingTest("""
                       def foo(IOException b) {
                         def x = switch (b) {
                           case Throwable -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  @Test
  public void testUntypedCondition() {
    highlightingTest("""
                       def foo(b) {
                         def x = <weak_warning>switch</weak_warning> (b) {
                           case 10 -> 1
                         }
                       }
                       """, GrSwitchExhaustivenessCheckInspection.class);
  }

  @Test
  public void testPlainSwitchStatementIsNotHighligted() {
    highlightingTest("""
                       void main(String[] args) {
                              switch (10) {
                                  case 20:
                                      return
                              }
                           }""");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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
}
