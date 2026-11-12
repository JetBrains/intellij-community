// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.GroovyVersionBasedTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class Groovy30HighlightingTest extends GroovyVersionBasedTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0_REAL_JDK);

  public Groovy30HighlightingTest() {
    super(projectFixture, TestUtils.getAbsoluteTestDataPath() + "highlighting/v30/");
  }

  @Test
  public void testDefaultMethodInInterfaces() {
    highlightingTest("""
                       import groovy.transform.CompileStatic

                       interface I {
                           default int bar() {
                               2
                           }
                       }

                       @CompileStatic
                       interface I2 {
                           default int bar() {
                               2
                           }
                       }
                       """);
  }

  @Test
  public void testDefaultModifier() {
    highlightingTest("""
                       default interface I {
                       }

                       trait T {
                           <warning descr="Modifier 'default' makes sense only in interface's methods">default</warning> int bar() {
                               2
                           }
                       }

                       class C {
                           <warning descr="Modifier 'default' makes sense only in interface's methods">default</warning> int bar() {
                               2
                           }
                       }
                       """);
  }

  @Test
  public void testSamWithDefaultModifier() {
    highlightingTest("""
                       interface I {
                           int foo()\s
                           default int bar() {
                               2
                           }
                       }

                       I i = {3}
                       """);
  }

  @Test
  public void testMethodReferenceToSAMConversion() {
    highlightingTest("""
                       class A {
                         def String m(){

                         }
                       }
                       List<A> list = []

                       list.sort(Comparator.comparing(A::m))
                       """, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testMethodReferenceToSAMConversion2() {
    highlightingTest("""
                       class A {
                         def String m(){

                         }
                       }
                       List<A> list = []
                       def c = A::m
                       list.sort(Comparator.comparing(c))
                       """, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testMethodReferenceToSAMConversionWithOverload() {
    highlightingTest("""
                       class A {
                         String m(Integer i){
                           return null
                         }
                        \s
                         Integer m(Thread i){
                           return null
                         }
                       }

                       interface SAM<T> {
                         T m(Integer a);
                       }

                       def <T> T foo(SAM<T> sam) {
                       }

                       def a = new A()
                       foo(a::m).toUpperCase()
                       """, GrUnresolvedAccessInspection.class);
  }

  @Test
  public void testConstructorReferenceStaticAccess() {
    fileHighlightingTest(GrUnresolvedAccessInspection.class);
  }

  @Test
  public void testIllegalSingleArgumentLambda() {
    fileHighlightingTest();
  }

  @Test
  public void testTypeUseInAnnotationDescription() {
    fileHighlightingTest();
  }

  @Test
  public void testFinalInTrait() {
    highlightingTest("""
                       trait ATrain {
                           final String getName() { 'foo' }
                       }
                       class Foo implements ATrain {
                           String getName() { "qq" }
                       }
                       """);
  }

  @Test
  public void testFinalInTrait2() {
    highlightingTest("""
                       trait ATrain {
                           final String getName() { 'foo' }
                       }
                       class Foo implements ATrain {
                       }
                       class Bar extends Foo {
                           <error>String getName()</error> { 'bar' }
                       }
                       """);
  }

  @Test
  public void testFinalInTrait3() {
    highlightingTest("""
                       trait ATrain {
                           final String getName() { 'foo' }
                       }
                       class Foo implements ATrain {
                           String getName() { "qq" }
                       }
                       class Bar extends Foo {
                           String getName() { 'bar' }
                       }""");
  }

  @Test
  public void testSealed() {
    highlightingTest("""
                       <error>sealed</error> class Foo {}

                       <error>non-sealed</error> trait Bar {}
                       """);
  }

  @Test
  public void testPermits() {
    highlightingTest("""
                       <error>sealed</error> class Foo <error>permits</error> Bar {}

                       class Bar extends Foo {}
                       """);
  }

  @Test
  public void testSwitchExpression() {
    highlightingTest("""
                       def x = <error>switch</error> (10) {
                       }""");
  }

  @Test
  public void testSwitchWithMultipleExpressions() {
    highlightingTest("""
                       switch (10) {
                         case <error>20, 30</error>:
                           break
                       }""");
  }

  @Test
  public void testEnhancedRange() {
    highlightingTest("""
                       <error>1<..<2</error>
                       """);
  }

  @Test
  public void testLiteralWithoutLeadingZero() {
    highlightingTest("""
                       <error>.5555d</error>
                       """);
  }

  @Test
  public void testRecordDefinition() {
    highlightingTest("""
                       <error>record</error> X() {}
                       """);
  }

  @Test
  public void testIDEA_285153() {
    highlightingTest("""
                       <error>class MyClass implements MyInterface</error> {
                       }

                       interface MyInterface {
                           void myAbstractMethod()

                           default void myDefaultMethod() {}
                       }""");
  }

  @Test
  public void testStaticallyImportedMethodWithDELEGATE_ONLY() {
    highlightingTest("""
                       import groovy.transform.CompileStatic
                       import static java.lang.String.valueOf

                       @CompileStatic
                       class Main {

                         static void func(@DelegatesTo(value = Main, strategy = Closure.DELEGATE_ONLY) Closure<String> cl) {}

                         static void main(String[] args) {
                           func {
                             valueOf(3)\s
                           }
                         }
                       }""");
  }

  @Test
  public void testIncompatibleTypeOfArrayDimension() {
    highlightingTest("""
                       static void main(String[] args) {
                         def a = new String[<warning descr="Cannot assign 'int' to 'ArrayList'">[]</warning>]
                         double doub = 1.0
                         def b = new int[<warning descr="Cannot assign 'int' to 'double'">doub</warning>]
                         def c = new int[10]
                         def d = new int[(1+1)][(1+3)]
                       }
                       """, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testIncompatibleTypeOfArrayInitializer() {
    LightGroovyFixtures.addCompileStatic(getFixture());
    highlightingTest("""
                       import groovy.transform.CompileStatic
                       
                       class A {}
                       
                       static void main(String[] args) {
                          def a = <error descr="Multi-dimensional array initializers are available in Groovy 5.0 or later">new String[]{
                          {"a"},
                          {},
                          "foo"
                          }</error>
                       
                          def b = new A[][]{<warning descr="Cannot assign 'String' to 'A[]'">"a"</warning>}
                       
                          def c = new A[]{
                          <warning descr="Cannot assign 'Integer' to 'A'">1</warning>
                          }
                       
                          def d = <error descr="Multi-dimensional array initializers are available in Groovy 5.0 or later">new A[][]{
                          {},
                          {<warning descr="Cannot assign 'Object' to 'A'">new Object()</warning>}
                          }</error>
                       
                          def e = <error descr="Multi-dimensional array initializers are available in Groovy 5.0 or later">new String[][]{"str", 1, {"strInsideInitializer"}}</error>
                       }
                       
                       @CompileStatic
                       void anotherMain() {
                          def a = <error descr="Multi-dimensional array initializers are available in Groovy 5.0 or later">new String[]{
                          {"a"},
                          {},
                          "foo"
                          }</error>
                       
                          def b = new A[][]{<error descr="Cannot assign 'String' to 'A[]'">"a"</error>}
                       
                          def c = new A[]{
                          <error descr="Cannot assign 'Integer' to 'A'">1</error>
                          }
                       
                          def d = <error descr="Multi-dimensional array initializers are available in Groovy 5.0 or later">new A[][]{
                          {},
                          {<error descr="Cannot assign 'Object' to 'A'">new Object()</error>}
                          }</error>
                       
                          def e = <error descr="Multi-dimensional array initializers are available in Groovy 5.0 or later">new String[][]{
                          <error descr="Cannot assign 'String' to 'String[]'">"str"</error>,
                          <error descr="Cannot assign 'Integer' to 'String[]'">1</error>,
                          {"strInsideInitializer"}
                          }</error>
                       }
                       """, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testClosureInsideArrayInitializer() { fileHighlightingTest(); }
}
