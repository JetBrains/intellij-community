// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixtureKt.typeInferenceFixture;

@TestApplication
public class SwitchExpressionTypeInferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0);
  private final TestFixture<TypeInferenceFixture> inference = typeInferenceFixture(projectFixture);

  @Test
  public void testSimple() {
    inference.get().doTest("""
             def xx = switch(10) {
                 case 10 -> 10
             }
             x<caret>x
             """, "java.lang.Integer");
  }

  @Test
  public void testLUB() {
    inference.get().doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             def xx = switch(10) {
               case 10 -> new B()
               case 20 -> new C()
             }
             x<caret>x
             """, "A");
  }

  @Test
  public void testBlock() {
    inference.get().doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             def xx = switch(10) {
               case 10 -> {
                 new B()
               }
               case 20 -> new C()
             }
             x<caret>x
             """, "A");
  }

  @Test
  public void testYield() {
    inference.get().doTest("""
             def xx = switch(10) {
                 case 10 -> yield 10
             }
             x<caret>x
             """, "java.lang.Integer");
  }

  @Test
  public void testYieldInBlock() {
    inference.get().doTest("""
             def xx = switch(10) {
                 case 10 -> {
                   yield 10
                 }
             }
             x<caret>x
             """, "java.lang.Integer");
  }

  @Test
  public void testConditionalYield() {
    inference.get().doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             def xx = switch(10) {
                 case 10 -> {
                   if (true) {
                     yield new B()
                   } else {
                     yield new C()
                   }
                 }
             }
             x<caret>x
             """, "A");
  }

  @Test
  public void testFlowThroughSwitch() {
    inference.get().doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             
             def bb = 0
             
             def x = switch (a) {
                 case 20 -> bb = new B()
                 default -> bb = new C()
             }
             b<caret>b
             """, "A");
  }

  @Test
  public void testNestedYields() {
    inference.get().doTest("""
             def xx = switch (10)  {
                 case 1..10 -> {
                     def r = switch (40) {
                         case 20 -> yield ""
                     }
                 }
                 default -> yield 50
             }
             x<caret>x
             """, "java.lang.Integer");
  }

  @Test
  public void testTypeSwitching() {
    inference.get().doTest("""
             def xx
             
             def _ = switch (xx) {
                 case String -> x<caret>x
             }
             """, "java.lang.String");
  }

  @Test
  public void testSwitchOnSeveralTypes() {
    inference.get().doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             
             def xx
             
             def _ = switch (xx) {
                 case B, C -> x<caret>x
             }
             """, "A");
  }

  @Test
  public void testMatchInOtherBranch() {
    inference.get().doTest("""
             def foo(xx) {
                 def _ = switch (xx) {
                     case String  -> xx.charAt(10)
                     case Integer -> x<caret>x
                 }
             }
             """, "java.lang.Integer");
  }

  @Test
  public void testMatchOnTypesInSwitchStatement() {
    inference.get().doTest("""
             def foo(xx) {
                 switch (xx) {
                     case String  -> xx.charAt(10)
                     case Integer -> x<caret>x
                 }
             }
             """, "java.lang.Integer");
  }

  @Test
  public void testMatchingWithColons() {
    inference.get().doTest("""
             class A {}
             class B extends A {}
             class C extends A {}
             
             def foo(xx) {
                 def _ = switch (xx) {
                     case B:
                     case C:
                       yield x<caret>x
                 }
             }
             """, "A");
  }
}
