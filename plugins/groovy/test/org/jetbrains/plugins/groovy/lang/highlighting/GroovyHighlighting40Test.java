// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.confusing.UnnecessaryQualifiedReferenceInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovyHighlighting40Test implements HighlightingTest {
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

  @Test
  public void testPermitsWithoutSealed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                          public interface Bar {
                              static void foo() {
                                  System.out.println("20");
                              }
                          }
                          """);
    myFixture.enableInspections(UnnecessaryQualifiedReferenceInspection.class);
    highlightingTest("""
                       class A implements Bar {
                           void bar() {
                               Bar.foo()
                           }
                       }
                       """);
  }

  @Test
  public void testVarInForLoop() {
    highlightingTest("for (var i : []) {}");
  }

  @Test
  public void testTupleDeclaration() {
    highlightingTest(
      """
      void f() {
          def x = 0
          def y = 1
          (x, y) = [-1, 0]
          var (Integer a, b) = [1, 2]
          def (Integer c, d) = [3, 4]
  
          final (Integer e, f) = [5, 6]
      }
      """
    );
  }

  @Test
  public void testDuplicateNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("foo/Bar.groovy",
      """
      package foo;
      
      class Bar {
          public static String Date = "01.01.2000"
      }
      """);

    myFixture.configureByText("a.groovy", """
      import java.util.Date
      import java.sql.Date
      import static foo.Bar.Date
      
      class <error descr="Class 'Date' already exists in '<default package>'">Date</error> {}
      
      trait <error descr="Class 'Date' already exists in '<default package>'">Date</error> {}
      """);
    myFixture.testHighlighting(false, false, false);
  }

  @Test
  public void testUnnamedVariableDuplicateInVariableDefinitions() {
    highlightingTest("""
                       void f() {
                          def (_, <error descr="Variable '_' already defined">_</error>) = [1, 2]
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableDuplicateInVariableDefinitionsWithOuterScope() {
    highlightingTest("""
                       void f() {
                           def (_) = [1]
                            def <error descr="Variable '_' already defined">_</error> = 1
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableDuplicateInLambdaExpression() {
    highlightingTest("""
                       void f() {
                           def x = (<error descr="Variable '_' already defined">_</error>, <error descr="Variable '_' already defined">_</error>, a, b) -> a + b
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableDuplicateInLambdaExpressionWithScope() {
    highlightingTest("""
                       void f() {
                           def x = (_, a) -> {
                              def <error descr="Variable '_' already defined">_</error> = 1
                              println a
                           }
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableDuplicateInClosure() {
    highlightingTest("""
                       void f() {
                           def x = {a, <error descr="Variable '_' already defined">_</error>, <error descr="Variable '_' already defined">_</error> -> a }
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableDuplicateInClosureWithScope() {
    highlightingTest("""
                       void f() {
                           def x = {_ ->
                           def <error descr="Variable '_' already defined">_</error> = 1
                            }
                       }
                       """);
  }
}
