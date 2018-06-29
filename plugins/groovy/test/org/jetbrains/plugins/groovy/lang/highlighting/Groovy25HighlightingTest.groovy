// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class Groovy25HighlightingTest extends LightGroovyTestCase implements HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_5
  final String basePath = TestUtils.testDataPath + 'highlighting/v25/'

  void 'test duplicating named params'() { highlightingTest() }
  void 'test duplicating named params with setter'() { highlightingTest() }
  void 'test two identical named delegates'() { highlightingTest() }
  void 'test duplicating named delegates with usual parameter'() { highlightingTest() }

  void 'test named params type check'() { highlightingTest() }
  void 'test named params type check with setter'() { highlightingTest() }

  void 'test named delegate without properties'() { highlightingTest() }

  void 'test immutable fields'() { highlightingTest() }

  void 'test immutable options absent field'() { highlightingTest() }

  void 'test immutable constructor'() {
    highlightingTest '''
import groovy.transform.Immutable

@Immutable
class C {
    <error descr="Explicit constructors are not allowed for @Immutable class">C</error>(){}
}
'''
  }

  void 'test getter in immutable'() {
    highlightingTest'''\
import groovy.transform.Immutable

@Immutable
class A {
  String immutable
  private String mutable

  String <error descr="Repetitive method name 'getImmutable'">getImmutable</error>() {immutable}
  String getMutable() {mutable}
}
'''
  }

  void 'test getter in immutable 2'() {
    highlightingTest'''\
import groovy.transform.Immutable

@Immutable
class A {
  String immutable

  int <error descr="Repetitive method name 'getImmutable'">getImmutable</error>() {1}
}
'''
  }

  void 'test tuple constructor in immutable'() {
    highlightingTest'''\
@groovy.transform.Immutable 
class Foo { int a; String b }

@groovy.transform.CompileStatic
def m() {
  new Foo()
  new Foo<error descr="Constructor 'Foo' in 'Foo' cannot be applied to '(java.lang.Integer)'">(2)</error>
  new Foo(2, "3")
  new Foo<error descr="Constructor 'Foo' in 'Foo' cannot be applied to '(java.lang.Integer, java.lang.String, java.lang.Integer)'">(2, "3", 9)</error>
}'''
  }


  void  'test copy with in immutable'(){
    highlightingTest '''
@groovy.transform.ImmutableBase(copyWith = true)
class CopyWith {
  String stringProp
  Integer integerProp
}

def usage(CopyWith cw) {
  cw.copyWith(st<caret>ringProp: 'hello')
}
'''

    def ref = fixture.file.findReferenceAt(editor.caretModel.offset)
    assert ref
    def resolved = ref.resolve()
    assert resolved instanceof GrField
  }
}