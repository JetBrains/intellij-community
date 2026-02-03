// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.confusing.UnnecessaryQualifiedReferenceInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

public class GroovyHighlighting40Test extends LightGroovyTestCase implements HighlightingTest {
  public void testPermitsWithoutSealed() {
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

  public void testVarInForLoop() {
    highlightingTest("for (var i : []) {}");
  }

  public void testTupleDeclaration() {
    highlightingTest(
      """
      void f() {
          def x = 0
          def y = 1
          (x, y) = [-1, 0]
          var (Integer a, b) = [1, 2]
          def (Integer c, d) = [3, 4]
  
          <error descr="Tuple declaration should end with 'def' or 'var' modifier">final</error> (Integer e, f) = [5, 6]
      }
      """
    );
  }

  public void testDuplicateNames() {
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

  public void testUnnamedVariableDuplicateInVariableDefinitions() {
    highlightingTest("""
                       void f() {
                          def (_, <error descr="Variable '_' already defined">_</error>) = [1, 2]
                       }
                       """);
  }

  public void testUnnamedVariableDuplicateInVariableDefinitionsWithOuterScope() {
    highlightingTest("""
                       void f() {
                           def (_) = [1]
                            def <error descr="Variable '_' already defined">_</error> = 1
                       }
                       """);
  }

  public void testUnnamedVariableDuplicateInLambdaExpression() {
    highlightingTest("""
                       void f() {
                           def x = (<error descr="Variable '_' already defined">_</error>, <error descr="Variable '_' already defined">_</error>, a, b) -> a + b
                       }
                       """);
  }

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

  public void testUnnamedVariableDuplicateInClosure() {
    highlightingTest("""
                       void f() {
                           def x = {a, <error descr="Variable '_' already defined">_</error>, <error descr="Variable '_' already defined">_</error> -> a }
                       }
                       """);
  }

  public void testUnnamedVariableDuplicateInClosureWithScope() {
    highlightingTest("""
                       void f() {
                           def x = {_ ->
                           def <error descr="Variable '_' already defined">_</error> = 1
                            }
                       }
                       """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_4_0;
  }
}
