// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection

/**
 * @author Max Medvedev
 */
@CompileStatic
class GrMethodMayBeStaticTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST
  final GrMethodMayBeStaticInspection inspection = new GrMethodMayBeStaticInspection()

  void testSimple() {
    doTest('''\
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
''')
  }

  void 'test abstract method with code block no error'() {
    doTest '''
abstract class A {
  abstract foo() <error descr="Abstract methods must not have body">{
    1 + 2
  }</error>
}
'''
  }

  private void doTest(final String text) {
    myFixture.configureByText('_.groovy', text)
    myFixture.enableInspections(inspection)
    myFixture.checkHighlighting(true, false, false)
  }

  void testOtherClasses() {
    myFixture.addFileToProject('Fooo.groovy', '''\
class Fooo {
  static def foo() {}
}
''')
    doTest('''\
class Bar {
  static def abc() {}
  def <warning descr="Method may be static">bar</warning>() {
    abc()
    Fooo.foo()
  }
}
''')
  }

  void testThis() {
    doTest('''\
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
''')
  }

  void testSuper() {
    doTest('''\
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
''')
  }

  void testUnresolvedRef() {
    doTest('''\
class Bar {
  def b() {
    print unresolved
  }
  def <warning descr="Method may be static">abc</warning>() {
    print 2
  }
}
''')
  }

  void 'test trait methods'() {
    doTest '''\
trait A {
  def foo() {1}
  abstract bar()
}
'''
  }

  void 'test trait methods with'() {
    inspection.myIgnoreTraitMethods = false
    doTest '''\
trait A {
  def <warning descr="Method may be static">foo</warning>() {1}
  abstract bar()
}
'''
  }
}
