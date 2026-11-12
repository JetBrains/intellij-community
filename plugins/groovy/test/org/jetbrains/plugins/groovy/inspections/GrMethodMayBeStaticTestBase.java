// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

/**
 * The tests of {@link GrMethodMayBeStaticInspection} that each subclass runs with its own descriptor.
 */
public abstract class GrMethodMayBeStaticTestBase {
  private final GrMethodMayBeStaticInspection inspection = new GrMethodMayBeStaticInspection();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture;

  protected GrMethodMayBeStaticTestBase(TestFixture<LightJavaProject> projectFixture) {
    codeInsightFixture = lightGroovyFixture(projectFixture);
  }

  @Test
  public void testSimple() {
    doTest("""
             class A {
               def <warning descr="Method may be static">foo</warning>() {
                 print 2
               }
             
               def <warning descr="Method may be static">bar</warning>() {
                 [1, 2 ].each {3}
               }
             
               def <warning descr="Method may be static">abc</warning>() {
                 new A().bar()
               }
             
               def cdef() {
                 bar()
               }
             
               def x() {
                 Inner ref = null
               }
             
               def <warning descr="Method may be static">y</warning>() {
                 staticMethod()
               }
             
               def <warning descr="Method may be static">z</warning>() {
                 StaticInner i = null
               }
             
               def q() {
                 staticMethod()
                 Inner i
               }
             
               class Inner {}
             
               static class StaticInner{}
             
               static staticMethod(){}
             }
             """);
  }

  @Test
  public void testAbstractMethodWithCodeBlockNoError() {
    doTest("""
             abstract class A {
               abstract foo() <error descr="Abstract methods must not have body">{
                 1 + 2
               }</error>
             }
             """);
  }

  protected void doTest(final String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", text);
    myFixture.enableInspections(getInspection());
    myFixture.checkHighlighting(true, false, false);
  }

  private GrMethodMayBeStaticInspection getInspection() {
    return inspection;
  }

  @Test
  public void testOtherClasses() {
    codeInsightFixture.get().addFileToProject("Fooo.groovy", """
      class Fooo {
        static def foo() {}
      }
      """);
    doTest("""
             class Bar {
               static def abc() {}
               def <warning descr="Method may be static">bar</warning>() {
                 abc()
                 Fooo.foo()
               }
             }
             """);
  }

  @Test
  public void testThis() {
    doTest("""
             class Foo {
               void <warning descr="Method may be static">barx</warning>() {
                 this.abc()
               }
             
               void cde() {
                 this.bar()
               }
             
               static abc(){}
               void bar(){}
             }
             """);
  }

  @Test
  public void testSuper() {
    doTest("""
             class Base {
                 void var() {}
             
                 static foo() {}
             }
             
             class Bar extends Base {
                 Bar <warning descr="Method may be static">b</warning>() {
                     super.foo()
                 }
             
                 Bar c() {
                     super.var()
                 }
             }
             """);
  }

  @Test
  public void testUnresolvedRef() {
    doTest("""
             class Bar {
               def b() {
                 print unresolved
               }
               def <warning descr="Method may be static">abc</warning>() {
                 print 2
               }
             }
             """);
  }

  @Test
  public void testTraitMethods() {
    doTest("""
             trait A {
               def foo() {1}
               abstract bar()
             }
             """);
  }

  @Test
  public void testTraitMethodsWith() {
    getInspection().myIgnoreTraitMethods = false;
    doTest("""
             trait A {
               def <warning descr="Method may be static">foo</warning>() {1}
               abstract bar()
             }
             """);
  }
}
