// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.ide.structureView.impl.java.KindSorter;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.StructureViewComponentTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyStructureViewTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSyntheticMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      class Foo {
        int prop

        def Foo(int a, int b = 2) {}

        def foo(int a, int b = 2) {}
      }
      """);
    StructureViewComponentTestUtil.testStructureView(myFixture, component -> {
      component.setActionActive(KindSorter.ID, true);
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

  @Test
  public void testInheritedSynthetic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    StructureViewComponentTestUtil.testStructureView(myFixture, component -> {
      component.setActionActive(KindSorter.ID, true);
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

  @Test
  public void testTupleConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package groovy.transform; public @interface TupleConstructor{}");
    myFixture.configureByText("a.groovy", """
      @groovy.transform.TupleConstructor
      class Foo {
        int prop
        void foo() {}
      }
      """);
    StructureViewComponentTestUtil.testStructureView(myFixture, component -> {
      component.setActionActive(KindSorter.ID, true);
      component.setActionActive(InheritedMembersNodeProvider.ID, false);
      PlatformTestUtil.assertTreeEqual(component.getTree(), """
        -a.groovy
         -Foo
          foo(): void
          prop: int
        """);
    });
  }

  @Test
  public void testTraitMembers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    StructureViewComponentTestUtil.testStructureView(myFixture, component -> {
      component.setActionActive(InheritedMembersNodeProvider.ID, false);
      PlatformTestUtil.assertTreeEqual(component.getTree(), """
          -_.groovy
           -C
            ownMethod(): void
          """);
    });
  }
}
