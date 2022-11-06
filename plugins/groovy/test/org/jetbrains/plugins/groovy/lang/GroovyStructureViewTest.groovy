/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang

import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.InheritedMembersNodeProvider
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.Consumer

import static com.intellij.testFramework.PlatformTestUtil.assertTreeEqual
class GroovyStructureViewTest extends LightJavaCodeInsightFixtureTestCase {

  void testSyntheticMethods() {
    myFixture.configureByText 'a.groovy', '''
class Foo {
  int prop

  def Foo(int a, int b = 2) {}

  def foo(int a, int b = 2) {}
}
'''
    myFixture.testStructureView(new Consumer<StructureViewComponent>() {
      @Override
      void consume(StructureViewComponent component) {
        component.setActionActive(InheritedMembersNodeProvider.ID, false)
        assertTreeEqual(component.getTree(), """-a.groovy
 -Foo
  Foo(int, int)
  foo(int, int): Object
  prop: int
""")
      }
    })
  }

  void testInheritedSynthetic() {
    myFixture.configureByText 'a.groovy', '''
class Foo {
  int prop
  def Foo(int a, int b = 2) {}
  def foo(int a, int b = 2) {}
}
class Bar extends Foo {
  def bar(int x = 239) {}
}
'''
    myFixture.testStructureView(new Consumer<StructureViewComponent>() {
      @Override
      void consume(StructureViewComponent component) {
        component.setActionActive(InheritedMembersNodeProvider.ID, true)
        assertTreeEqual(component.getTree(), """-a.groovy
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
""")
      }
    })

  }

  void testTupleConstructor() {
    myFixture.addClass 'package groovy.transform; public @interface TupleConstructor{}'
    myFixture.configureByText 'a.groovy', '''
@groovy.transform.TupleConstructor
class Foo {
  int prop
  void foo() {}
}
'''
    myFixture.testStructureView(new Consumer<StructureViewComponent>() {
      @Override
      void consume(StructureViewComponent component) {
        component.setActionActive(InheritedMembersNodeProvider.ID, false)
        assertTreeEqual(component.getTree(), """-a.groovy
 -Foo
  foo(): void
  prop: int
""")
      }
    })

  }

  void testTraitMembers() {
    myFixture.addFileToProject 'T.groovy', '''\
trait T { 
  def tProperty
  def tMethod() {}
}
'''
    myFixture.configureByText '_.groovy', '''\
class C implements T {
  void ownMethod() {}
}
'''
    myFixture.testStructureView {
      it.setActionActive InheritedMembersNodeProvider.ID, false
      assertTreeEqual(it.tree, """-_.groovy
 -C
  ownMethod(): void
""")
    }
  }
}
