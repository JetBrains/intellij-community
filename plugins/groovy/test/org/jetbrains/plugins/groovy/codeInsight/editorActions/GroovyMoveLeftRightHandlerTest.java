// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovyMoveLeftRightHandlerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  private void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", before);
    myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT);
    if (after != null) {
      myFixture.checkResult(after);
      myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT);
    }
    myFixture.checkResult(before);
  }

  private void doTest(String before) {
    doTest(before, null);
  }

  @Test
  public void test_annotation_argument() {
    doTest("@MyAnno(a = <caret>\"e\", b = \"f\") def a", "@MyAnno(b = \"f\", a = <caret>\"e\") def a");
    doTest("@MyAnno(a = \"e\", <caret>b = \"f\") def a");
  }

  @Test
  public void test_annotation_array_initializer() {
    doTest("@MyAnno([<caret>1, 2, 3]) def a", "@MyAnno([2, <caret>1, 3]) def a");
    doTest("@MyAnno(a = [1, 2, <caret>3], b = 2) def a");
  }

  @Test
  public void test_argument_list() {
    doTest("def foo(def...a) {}; foo(1, <caret>2, 3)", "def foo(def...a) {}; foo(1, 3, <caret>2)");
    doTest("def foo(def...a) {}; foo(1, <caret>a: 2, 3)", "def foo(def...a) {}; foo(1, 3, <caret>a: 2)");
  }

  @Test
  public void test_enum_definition() {
    doTest("enum E {ONE, <caret>TWO, THREE}", "enum E {ONE, THREE, <caret>TWO}");
    doTest("enum E {ONE, TWO, <caret>THREE}");
  }

  @Test
  public void test_list_or_map() {
    doTest("[<caret>1, 2, 3]", "[2, <caret>1, 3]");
    doTest("[<caret>a: 1, b: 2, c: 3]", "[b: 2, <caret>a: 1, c: 3]");
    doTest("[<caret>1, a: 2, 3]", "[a: 2, <caret>1, 3]");
  }

  @Test
  public void test_method_modifier_list() {
    doTest("<caret>synchronized @Deprecated def foo() {}", "@Deprecated <caret>synchronized def foo() {}");
    doTest("synchronized @Deprecated <caret>def foo() {}");
  }

  @Test
  public void test_class_modifier_list() {
    doTest("<caret>public @Deprecated class A {}", "@Deprecated <caret>public class A {}");
    doTest("public <caret>@Deprecated class A {}");
  }

  @Test
  public void test_parameter_list() {
    doTest("def foo(<caret>a, b = 2, c){}", "def foo(b = 2, <caret>a, c){}");
    doTest("def foo(a, b = 2, <caret>c){}");
  }

  @Test
  public void test_implements_list() {
    doTest("class A implements <caret>Foo, Bar, Baz {}", "class A implements Bar, <caret>Foo, Baz {}");
    doTest("class A implements Foo, Bar, <caret>Baz {}");
  }

  @Test
  public void test_extends_list() {
    doTest("class B extends <caret>Foo, Bar, Baz {}", "class B extends Bar, <caret>Foo, Baz {}");
    doTest("class B extends Foo, Bar, <caret>Baz {}");
  }

  @Test
  public void test_throws_list() {
    doTest("def foo() throws <caret>Foo, Bar, Baz {}", "def foo() throws Bar, <caret>Foo, Baz {}");
    doTest("def foo() throws Foo, Bar, <caret>Baz {}");
  }

  @Test
  public void test_type_parameter_list() {
    doTest("class A<<caret>T, U, K> {}", "class A<U, <caret>T, K> {}");
    doTest("class A<T, U, <caret>K> {}");
  }

  @Test
  public void test_type_argument_list() {
    doTest("new A<<caret>K,V>()", "new A<V,<caret>K>()");
    doTest("new A<K,<caret>V>()");
  }

  @Test
  public void test_variable_declaration() {
    doTest("def a = 1, <caret>b = 2, c", "def a = 1, c, <caret>b = 2");
    doTest("def a = 1, b = 2, <caret>c");
  }

  @Test
  public void test_binary_expression() {
    doTest("<caret>1 + 2 + 3");
    doTest("1 + <caret>2 + 3");
  }

  @Test
  public void test_for_update() {
    doTest("for (;; <caret>1, 2, 3) {}", "for (;; 2, <caret>1, 3) {}");
  }

  @Test
  public void test_array_initializer() {
    doTest("new int[]{<caret>1, 2, 3}", "new int[]{2, <caret>1, 3}");
  }
}
