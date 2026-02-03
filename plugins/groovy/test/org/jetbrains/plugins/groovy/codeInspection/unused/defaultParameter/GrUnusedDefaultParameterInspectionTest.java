// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.unused.defaultParameter;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public class GrUnusedDefaultParameterInspectionTest /* extends LightGroovyTestCase */ {
  //@Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testSimple0() {
    testHighlighting("""
                       def foo(a = 1, b = 2) {}
                       foo()
                       """);
  }

  public void testSimple1() {
    testHighlighting("""
                       def foo(a = <warning descr="Default parameter is not used">1</warning>, b = 2) {}
                       foo(10)
                       """);
  }

  public void testSimple2() {
    testHighlighting("""
                       def foo(a = <warning descr="Default parameter is not used">1</warning>, b = <warning descr="Default parameter is not used">2</warning>) {}
                       foo(10, 20)
                       """);
  }

  public void testMapArguments() {
    testHighlighting("""
                       def foo(a = <warning descr="Default parameter is not used">1</warning>, b = 2) {}
                       foo(a: 2, b: 3, c: 4)
                       """);
  }

  public void testDefaultParameterInTheMiddleUsed() {
    testHighlighting("""
                       
                       def foo(a, b = 2, c) {}
                       foo(1, 2)
                       """);
  }

  public void testDefaultParameterInTheMiddleUnused() {
    testHighlighting("""
                       
                       def foo(a, b = <warning descr="Default parameter is not used">2</warning>, c) {}
                       foo(1, 2, 3)
                       """);
  }

  public void testDelegate0() {
    testHighlighting("""
                       
                       class A {
                           def foo(a = 1, b) { a + b }
                       }
                       class B {
                           @Delegate
                           A dd
                       }
                       
                       new B().foo(1)
                       """);
  }

  public void testDelegate1() {
    testHighlighting("""
                       
                       class A {
                           def foo(a = <warning descr="Default parameter is not used">1</warning>, b) { a + b }
                       }
                       class B {
                           @Delegate
                           A dd
                       }
                       
                       new B().foo(1, 2)
                       """);
  }

  public void testSuperMethodChildUsage1() {
    testHighlighting("""
                       class A {
                         def foo(a, b = 2) { a + b }
                       }
                       class B extends A {}
                       
                       new B().foo(1)
                       """);
  }

  public void testSuperMethodChildUsage2() {
    testHighlighting("""
                       class A {
                         def foo(a, b = <warning descr="Default parameter is not used">2</warning>) { a + b }
                       }
                       class B extends A {}
                       
                       new B().foo(1, 2)
                       """);
  }

  public void testSuperMethodChildUsage3() {
    testHighlighting("""
                       class A {
                         def foo(a, b = <warning descr="Default parameter is not used">2</warning>) { a + b }
                       }
                       class B extends A {
                         def foo(a, b) {}
                       }
                       
                       new B().foo(1, 2)
                       """);
  }

  public void testReflectedMethodDoNotHaveSuperMethod() {
    testHighlighting("""
                       
                       class A {
                         def foo(a, b) {}\s
                       }
                       
                       class B extends A {
                         def foo(a, b = <warning descr="Default parameter is not used">2</warning>) {}
                       }
                       
                       def test(A a) {
                         a.foo(1, 2)
                       }
                       """);
  }

  public void testReflectedMethodHasSuperMethod() {
    testHighlighting("""
                       
                       class A {
                         def foo(a) {}\s
                       }
                       
                       class B extends A {
                         def foo(a, b = 2) {}
                       }
                       """);
  }

  public void testReflectedConstructor() {
    testHighlighting("""
                       
                       class A {
                         A(a) {}\s
                       }
                       
                       class B extends A {
                         B(a, b = <warning descr="Default parameter is not used">2</warning>) { super("") }
                       }
                       """);
  }

  public void testOverrideMethodWithDefaultParameters() {
    testHighlighting("""
                       class A {
                         def foo(a, b = <warning descr="Default parameter is not used">2</warning>) {}
                       }
                       class B extends A {
                         def foo(a, b = 2) {}
                       }
                       """);
  }

  private void testHighlighting(String text) {
    //myFixture.configureByText("_.groovy", text);
    //myFixture.enableInspections(new GrUnusedDefaultParameterInspection());
    //myFixture.checkHighlighting();
  }
}