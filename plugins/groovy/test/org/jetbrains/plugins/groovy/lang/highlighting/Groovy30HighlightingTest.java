// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.GroovyVersionBasedTest;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class Groovy30HighlightingTest extends GroovyVersionBasedTest {
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

  public void testConstructorReferenceStaticAccess() {
    fileHighlightingTest(GrUnresolvedAccessInspection.class);
  }

  public void testIllegalSingleArgumentLambda() {
    fileHighlightingTest();
  }

  public void testTypeUseInAnnotationDescription() {
    fileHighlightingTest();
  }

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

  public void testSealed() {
    highlightingTest("""
                       <error>sealed</error> class Foo {}

                       <error>non-sealed</error> trait Bar {}
                       """);
  }

  public void testPermits() {
    highlightingTest("""
                       <error>sealed</error> class Foo <error>permits</error> Bar {}

                       class Bar extends Foo {}
                       """);
  }

  public void testSwitchExpression() {
    highlightingTest("""
                       def x = <error>switch</error> (10) {
                       }""");
  }

  public void testSwitchWithMultipleExpressions() {
    highlightingTest("""
                       switch (10) {
                         case <error>20, 30</error>:
                           break
                       }""");
  }

  public void testEnhancedRange() {
    highlightingTest("""
                       <error>1<..<2</error>
                       """);
  }

  public void testLiteralWithoutLeadingZero() {
    highlightingTest("""
                       <error>.5555d</error>
                       """);
  }

  public void testRecordDefinition() {
    highlightingTest("""
                       <error>record</error> X() {}
                       """);
  }

  public void testIDEA_285153() {
    highlightingTest("""
                       <error>class MyClass implements MyInterface</error> {
                       }

                       interface MyInterface {
                           void myAbstractMethod()

                           default void myDefaultMethod() {}
                       }""");
  }

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

  public void testClosureInsideArrayInitializer() { fileHighlightingTest(); }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0_REAL_JDK;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/v30/";
  }
}
