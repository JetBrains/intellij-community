// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.dataflow.GroovyVariableCanBeFinalInspection;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovyHighlighting50Test implements HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0_REAL_JDK);
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
  public void testPatternVariable() {
    highlightingTest("""
                   class X {
                     static class A {}
                     static class B extends A{}
                   
                     void simple() {
                       A a = new B()
                       if (a instanceof B <error descr="Variable 'a' already defined">a</error>) {}
                     }
                   
                     void externalVariable() {
                      A a = new B()
                      int c;
                      if (a instanceof B <error descr="Variable 'c' already defined">c</error>) {}
                     }
                   
                     void parameter(int c) {
                      A a = new B()
                      if (a instanceof B <error descr="Variable 'c' already defined">c</error>) {}
                     }
                   }
                   """);
  }

  @Test
  public void testIncompatibleTypeOfArrayInitializer() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addCompileStatic(myFixture);
    highlightingTest("""
                       import groovy.transform.CompileStatic
                       
                       class A {}
                       
                       static void main(String[] args) {
                          def a = new String[]{
                          <warning descr="Illegal initializer for 'String'">{"a"}</warning>,
                          <warning descr="Illegal initializer for 'String'">{}</warning>,
                          "foo"
                          }
                       
                          def b = new A[][]{<warning descr="Cannot assign 'String' to 'A[]'">"a"</warning>}
                       
                          def c = new A[]{
                          <warning descr="Cannot assign 'Integer' to 'A'">1</warning>
                          }
                       
                          def d = new A[][]{
                          {},
                          {<warning descr="Cannot assign 'Object' to 'A'">new Object()</warning>}
                          }
                       
                          def e = new String[][]{"str", 1, {"strInsideInitializer"}}
                       }
                       
                       @CompileStatic
                       void anotherMain() {
                          def a = new String[]{
                          <error descr="Illegal initializer for 'String'">{"a"}</error>,
                          <error descr="Illegal initializer for 'String'">{}</error>,
                          "foo"
                          }
                       
                          def b = new A[][]{<error descr="Cannot assign 'String' to 'A[]'">"a"</error>}
                       
                          def c = new A[]{
                          <error descr="Cannot assign 'Integer' to 'A'">1</error>
                          }
                       
                          def d = new A[][]{
                          {},
                          {<error descr="Cannot assign 'Object' to 'A'">new Object()</error>}
                          }
                       
                          def e = new String[][]{
                          <error descr="Cannot assign 'String' to 'String[]'">"str"</error>,
                          <error descr="Cannot assign 'Integer' to 'String[]'">1</error>,
                          {"strInsideInitializer"}
                          }
                       }
                       """, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testUnnamedVariableIsNotDuplicateInVariableDefinitions() {
    highlightingTest("""
                       void f() {
                           def (_, _) = [1, 2]
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableIsNotDuplicateInVariableDefinitionsWithOuterScope() {
    highlightingTest("""
                       void f() {
                           def (_) = [1]
                           def _ = 1
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableIsNotDuplicateInLambdaExpression() {
    highlightingTest("""
                       void f() {
                           def x = (_, _, a, b) -> a + b
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableIsNotDuplicateInLambdaExpressionWithScope() {
    highlightingTest("""
                       void f() {
                           def x = (_, _, a) -> {
                              def _ = 1
                              println a
                           }
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableIsNotDuplicateInClosure() {
    highlightingTest("""
                       void f() {
                           def x = {a, _, _ -> a }
                       }
                       """);
  }

  @Test
  public void testUnnamedVariableIsNotDuplicateInClosureWithScope() {
    highlightingTest("""
                       void f() {
                           def x = {_ ->
                           def _ = 1
                            }
                       }
                       """);
  }

  @Test
  public void testValDeclaration() {
    highlightingTest("""
                       <error descr="'val' declarations are available in Groovy 6.0 or later">val</error> x = 1
                       """);
  }

  @Test
  public void testModuleImport() {
    highlightingTest("""
                       <error descr="Module imports are available in Groovy 6.0 or later"><warning descr="Unused import">import module java.base</warning></error>
                       """);
  }

  @Test
  public void testAnnotatedImport() {
    highlightingTest("""
                       @Deprecated
                       <warning descr="Unused import">import java.lang.String</warning>
                       """);
  }

  @Test
  public void testAnnotatedLoop() {
    highlightingTest("""
                       import groovy.transform.<error descr="Cannot resolve symbol 'Parallel'">Parallel</error>
                       var items = [1, 2, 3]
                       <error descr="Loop annotations are available in Groovy 6.0 or later">@<error descr="Cannot resolve symbol 'Parallel'">Parallel</error></error>
                       <error descr="Loop annotations are available in Groovy 6.0 or later">@Deprecated</error>
                       for (item in items) { println(item) }
                       """);
  }

  @Test
  public void testSimpleVar() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GroovyVariableCanBeFinalInspection());
    highlightingTest("""
                       def method() {
                         var <warning descr="Variable 'x' can be final"><caret>x</warning> = 1
                         println x
                       }
                       """);
    myFixture.launchAction(myFixture.findSingleIntention("Make 'x' final"));
    myFixture.checkResult("""
                            def method() {
                              final var x = 1
                              println x
                            }
                            """);
  }

  @Test
  public void testAbstractMethodWithBody() {
    highlightingTest("""
                         interface A {
                           def foo()<error descr="Interface abstract methods must not have body">{}</error>
                         
                           private def privateMethod() {}
                         
                           static def staticMethod() {}
                         
                           default def defaultMethod() {}
                         
                           <error descr="Interface members are not allowed to be protected">protected</error> def protectedMethod() <error descr="Interface abstract methods must not have body">{}</error>
                         }

                         abstract class B {
                           abstract foo()<error descr="Abstract methods must not have body">{}</error>
                         }

                         class X {
                           def foo(){}
                         }
                         """);
  }

  @Test
  public void testCallingStaticMethodOnInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("com/acme/Foo.groovy", """
      package com.acme;
      
      interface Foo {
          static String fooMessage() {
              return "foo";
          }
      }""");
    myFixture.configureByText("a.groovy", """
      import static com.acme.Foo.fooMessage;
      
      class Capibara {
          static void main(String... args) {
              System.out.println(fooMessage())      // There should not be an error here
          }
      }
      interface Cromulent {
          static void x() {
              y()
          }
      
          static void y() {}
      }
      class Spurious implements Cromulent {
        void z() {
          <error descr="Calls to 'static' methods of super interfaces should be qualified">y</error>()
        }
      }
      """);
    myFixture.testHighlighting(false, false, false);
  }
}
