// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

public class GroovyMoveLeftRightHandlerTest extends LightGroovyTestCase {
  private void doTest(String before, String after) {
    getFixture().configureByText("_.groovy", before);
    getFixture().performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT);
    if (after != null) {
      getFixture().checkResult(after);
      getFixture().performEditorAction(IdeActions.MOVE_ELEMENT_LEFT);
    }
    getFixture().checkResult(before);
  }

  private void doTest(String before) {
    doTest(before, null);
  }

  public void test_annotation_argument() {
    doTest("@MyAnno(a = <caret>\"e\", b = \"f\") def a", "@MyAnno(b = \"f\", a = <caret>\"e\") def a");
    doTest("@MyAnno(a = \"e\", <caret>b = \"f\") def a");
  }

  public void test_annotation_array_initializer() {
    doTest("@MyAnno([<caret>1, 2, 3]) def a", "@MyAnno([2, <caret>1, 3]) def a");
    doTest("@MyAnno(a = [1, 2, <caret>3], b = 2) def a");
  }

  public void test_argument_list() {
    doTest("def foo(def...a) {}; foo(1, <caret>2, 3)", "def foo(def...a) {}; foo(1, 3, <caret>2)");
    doTest("def foo(def...a) {}; foo(1, <caret>a: 2, 3)", "def foo(def...a) {}; foo(1, 3, <caret>a: 2)");
  }

  public void test_enum_definition() {
    doTest("enum E {ONE, <caret>TWO, THREE}", "enum E {ONE, THREE, <caret>TWO}");
    doTest("enum E {ONE, TWO, <caret>THREE}");
  }

  public void test_list_or_map() {
    doTest("[<caret>1, 2, 3]", "[2, <caret>1, 3]");
    doTest("[<caret>a: 1, b: 2, c: 3]", "[b: 2, <caret>a: 1, c: 3]");
    doTest("[<caret>1, a: 2, 3]", "[a: 2, <caret>1, 3]");
  }

  public void test_method_modifier_list() {
    doTest("<caret>synchronized @Deprecated def foo() {}", "@Deprecated <caret>synchronized def foo() {}");
    doTest("synchronized @Deprecated <caret>def foo() {}");
  }

  public void test_class_modifier_list() {
    doTest("<caret>public @Deprecated class A {}", "@Deprecated <caret>public class A {}");
    doTest("public <caret>@Deprecated class A {}");
  }

  public void test_parameter_list() {
    doTest("def foo(<caret>a, b = 2, c){}", "def foo(b = 2, <caret>a, c){}");
    doTest("def foo(a, b = 2, <caret>c){}");
  }

  public void test_implements_list() {
    doTest("class A implements <caret>Foo, Bar, Baz {}", "class A implements Bar, <caret>Foo, Baz {}");
    doTest("class A implements Foo, Bar, <caret>Baz {}");
  }

  public void test_extends_list() {
    doTest("class B extends <caret>Foo, Bar, Baz {}", "class B extends Bar, <caret>Foo, Baz {}");
    doTest("class B extends Foo, Bar, <caret>Baz {}");
  }

  public void test_throws_list() {
    doTest("def foo() throws <caret>Foo, Bar, Baz {}", "def foo() throws Bar, <caret>Foo, Baz {}");
    doTest("def foo() throws Foo, Bar, <caret>Baz {}");
  }

  public void test_type_parameter_list() {
    doTest("class A<<caret>T, U, K> {}", "class A<U, <caret>T, K> {}");
    doTest("class A<T, U, <caret>K> {}");
  }

  public void test_type_argument_list() {
    doTest("new A<<caret>K,V>()", "new A<V,<caret>K>()");
    doTest("new A<K,<caret>V>()");
  }

  public void test_variable_declaration() {
    doTest("def a = 1, <caret>b = 2, c", "def a = 1, c, <caret>b = 2");
    doTest("def a = 1, b = 2, <caret>c");
  }

  public void test_binary_expression() {
    doTest("<caret>1 + 2 + 3");
    doTest("1 + <caret>2 + 3");
  }

  public void test_for_update() {
    doTest("for (;; <caret>1, 2, 3) {}", "for (;; 2, <caret>1, 3) {}");
  }

  public void test_array_initializer() {
    doTest("new int[]{<caret>1, 2, 3}", "new int[]{2, <caret>1, 3}");
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
