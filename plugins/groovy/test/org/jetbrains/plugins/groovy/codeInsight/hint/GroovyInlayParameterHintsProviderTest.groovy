/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class GroovyInlayParameterHintsProviderTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST
  String prefix

  @Override
  void setUp() {
    super.setUp()
    fixture.addFileToProject 'Foo.groovy', '''\
class Foo {
  Foo() {}
  Foo(a, b, c) {}
  void simple(a) {}
  void simple(a, b) {}
  void defaultArgs(a, b = 1,c) {}
  void mapArgs(Map m, a) {}
  void varArgs(a, def... b) {}
  void combo(Map m, a, b = 1, def ... c) {}
  def getAt(a) {}
}
'''
  }

  private void testInlays(String text, Map<Integer, String> expectedParameterHints) {
    fixture.configureByText '_.groovy', prefix ? prefix + text : text
    fixture.doHighlighting()
    def manager = ParameterHintsPresentationManager.instance
    def inlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
    def parameterHinInlays = inlays.findAll {
      manager.isParameterHint(it)
    }
    int prefixLength = prefix?.length() ?: 0
    def actualParameterHints = parameterHinInlays.collectEntries {
      [(it.offset - prefixLength): manager.getHintText(it)]
    }
    assert actualParameterHints == expectedParameterHints
  }

  void 'test method call expressions'() {
    prefix = '''\
def foo = new Foo()
foo.'''
    testInlays 'simple(null)', [7: 'a']
    testInlays 'defaultArgs(1, 2)', [12: 'a', 15: 'c']
    testInlays 'defaultArgs(1, 2, 3)', [12: 'a', 15: 'b', 18: 'c']
    testInlays 'mapArgs(foo: 1, null, bar: 2)', [16: 'a']
    testInlays 'varArgs(1, 2, 3, 4)', [8: 'a', 11: '...b']
    testInlays 'combo(1, foo: 10, 2, 3, 4, bar: 20, 5)', [6: 'a', 18: 'b', 21: '...c']
  }

  void 'test method call applications'() {
    prefix = '''\
def foo = new Foo()
foo.'''
    testInlays 'simple null', [7: 'a']
    testInlays 'defaultArgs 1, 2', [12: 'a', 15: 'c']
    testInlays 'defaultArgs 1, 2, 3', [12: 'a', 15: 'b', 18: 'c']
    testInlays 'mapArgs foo: 1, null, bar: 2 ', [16: 'a']
    testInlays 'varArgs 1, 2, 3, 4 ', [8: 'a', 11: '...b']
    testInlays 'combo 1, foo: 10, 2, 3, 4, bar: 20, 5', [6: 'a', 18: 'b', 21: '...c']
  }

  void 'test index expression'() {
    testInlays 'new Foo()[1]', [10: 'a']
  }

  void 'test new expression'() {
    testInlays 'new Foo(1, 2, 4)', [8: 'a', 11: 'b', 14: 'c']
  }

  void 'test constructor invocation'() {
    testInlays '''\
class Bar {
  Bar() {
    this(1, 4, 5)
  }

  Bar(a, b, c) {}
}
''', [31: 'a', 34: 'b', 37: 'c']
  }

  void 'test enum constants'() {
    testInlays '''\
enum Baz {
  ONE(1, 2),
  TWO(4, 3)
  Baz(a, b) {}
}
''', [17: 'a', 20: 'b', 30: 'a', 33: 'b']
  }

  void 'test no DGM inlays'() {
    testInlays '[].each {}', [:]
  }

  void 'test closure arguments'() {
    testInlays 'new Foo().simple {}', [:]
    testInlays 'new Foo().simple({})', [17: 'a']
    testInlays 'new Foo().simple null, {}', [17: 'a', 23: 'b']
  }
}
