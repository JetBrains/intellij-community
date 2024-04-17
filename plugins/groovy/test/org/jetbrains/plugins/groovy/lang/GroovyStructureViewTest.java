// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class GroovyStructureViewTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSyntheticMethods() {
    myFixture.configureByText("a.groovy", """
      class Foo {
        int prop

        def Foo(int a, int b = 2) {}

        def foo(int a, int b = 2) {}
      }
      """);
    myFixture.testStructureView(component -> {
      component.setActionActive(InheritedMembersNodeProvider.ID, false);
      PlatformTestUtil.assertTreeEqual(component.getTree(), """
        -a.groovy
         -Foo
          Foo(int, int)
          foo(int, int): Object
          prop: int
        """);
    });
  }

  public void testInheritedSynthetic() {
    myFixture.configureByText("a.groovy", """
      class Foo {
        int prop
        def Foo(int a, int b = 2) {}
        def foo(int a, int b = 2) {}
      }
      class Bar extends Foo {
        def bar(int x = 239) {}
      }
      """);
    myFixture.testStructureView(component -> {
      component.setActionActive(InheritedMembersNodeProvider.ID, true);
      PlatformTestUtil.assertTreeEqual(component.getTree(), """
        -a.groovy
         -Foo
          Foo(int, int)
          foo(int, int): Object
          getClass(): Class<?>
          hashCode(): int
          equals(Object): boolean
          clone(): Object
          toString(): String
          notify(): void
          notifyAll(): void
          wait(long): void
          wait(long, int): void
          wait(): void
          finalize(): void
          prop: int
         -Bar
          bar(int): Object
          foo(int, int): Object
          getClass(): Class<?>
          hashCode(): int
          equals(Object): boolean
          clone(): Object
          toString(): String
          notify(): void
          notifyAll(): void
          wait(long): void
          wait(long, int): void
          wait(): void
          finalize(): void
          prop: int
        """);
    });
  }

  public void testTupleConstructor() {
    myFixture.addClass("package groovy.transform; public @interface TupleConstructor{}");
    myFixture.configureByText("a.groovy", """
      @groovy.transform.TupleConstructor
      class Foo {
        int prop
        void foo() {}
      }
      """);
    myFixture.testStructureView(component -> {
      component.setActionActive(InheritedMembersNodeProvider.ID, false);
      PlatformTestUtil.assertTreeEqual(component.getTree(), """
        -a.groovy
         -Foo
          foo(): void
          prop: int
        """);
    });
  }

  public void testTraitMembers() {
    myFixture.addFileToProject("T.groovy", """
      trait T {\s
        def tProperty
        def tMethod() {}
      }
      """);
    myFixture.configureByText("_.groovy", """
      class C implements T {
        void ownMethod() {}
      }
      """);
    myFixture.testStructureView(component -> {
      component.setActionActive(InheritedMembersNodeProvider.ID, false);
      PlatformTestUtil.assertTreeEqual(component.getTree(), """
          -_.groovy
           -C
            ownMethod(): void
          """);
    });
  }
}
