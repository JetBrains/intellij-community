// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.editorActions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GroovyMoveLeftRightHandlerTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  private void doTest(String before, String after = null) {
    fixture.with {
      configureByText '_.groovy', before
      performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT)
      if (after != null) {
        checkResult after
        performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
      }
      checkResult before
    }
  }

  void 'test annotation argument'() {
    doTest '@MyAnno(a = <caret>"e", b = "f") def a',
           '@MyAnno(b = "f", a = <caret>"e") def a'
    doTest '@MyAnno(a = "e", <caret>b = "f") def a'
  }

  void 'test annotation array initializer'() {
    doTest '@MyAnno([<caret>1, 2, 3]) def a',
           '@MyAnno([2, <caret>1, 3]) def a'
    doTest '@MyAnno(a = [1, 2, <caret>3], b = 2) def a'
  }

  void 'test argument list'() {
    doTest 'def foo(def...a) {}; foo(1, <caret>2, 3)',
           'def foo(def...a) {}; foo(1, 3, <caret>2)'
    doTest 'def foo(def...a) {}; foo(1, <caret>a: 2, 3)',
           'def foo(def...a) {}; foo(1, 3, <caret>a: 2)'
  }

  void 'test enum definition'() {
    doTest 'enum E {ONE, <caret>TWO, THREE}',
           'enum E {ONE, THREE, <caret>TWO}'
    doTest 'enum E {ONE, TWO, <caret>THREE}'
  }

  void 'test list or map'() {
    doTest '[<caret>1, 2, 3]',
           '[2, <caret>1, 3]'
    doTest '[<caret>a: 1, b: 2, c: 3]',
           '[b: 2, <caret>a: 1, c: 3]'
    doTest '[<caret>1, a: 2, 3]',
           '[a: 2, <caret>1, 3]'
  }

  void 'test method modifier list'() {
    doTest '<caret>synchronized @Deprecated def foo() {}',
           '@Deprecated <caret>synchronized def foo() {}'
    doTest 'synchronized @Deprecated <caret>def foo() {}'
  }

  void 'test class modifier list'() {
    doTest '<caret>public @Deprecated class A {}',
           '@Deprecated <caret>public class A {}'
    doTest 'public <caret>@Deprecated class A {}'
  }

  void 'test parameter list'() {
    doTest 'def foo(<caret>a, b = 2, c){}',
           'def foo(b = 2, <caret>a, c){}'
    doTest 'def foo(a, b = 2, <caret>c){}'
  }

  void 'test implements list'() {
    doTest 'class A implements <caret>Foo, Bar, Baz {}',
           'class A implements Bar, <caret>Foo, Baz {}'
    doTest 'class A implements Foo, Bar, <caret>Baz {}'
  }

  void 'test extends list'() {
    doTest 'class B extends <caret>Foo, Bar, Baz {}',
           'class B extends Bar, <caret>Foo, Baz {}'
    doTest 'class B extends Foo, Bar, <caret>Baz {}'
  }

  void 'test throws list'() {
    doTest 'def foo() throws <caret>Foo, Bar, Baz {}',
           'def foo() throws Bar, <caret>Foo, Baz {}'
    doTest 'def foo() throws Foo, Bar, <caret>Baz {}'
  }

  void 'test type parameter list'() {
    doTest 'class A<<caret>T, U, K> {}',
           'class A<U, <caret>T, K> {}'
    doTest 'class A<T, U, <caret>K> {}'
  }

  void 'test type argument list'() {
    doTest 'new A<<caret>K,V>()', 'new A<V,<caret>K>()'
    doTest 'new A<K,<caret>V>()'
  }

  void 'test variable declaration'() {
    doTest 'def a = 1, <caret>b = 2, c', 'def a = 1, c, <caret>b = 2'
    doTest 'def a = 1, b = 2, <caret>c'
  }

  void 'test binary expression'() {
    doTest '<caret>1 + 2 + 3'
    doTest '1 + <caret>2 + 3'
  }
}
