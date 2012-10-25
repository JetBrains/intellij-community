/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.inspections

import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection
/**
 * @author Max Medvedev
 */
public class GrMethodMayBeStaticTest extends LightGroovyTestCase {
  final String basePath = null

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

  private void doTest(final String text) {
    myFixture.configureByText('_.groovy', text)

    myFixture.enableInspections(GrMethodMayBeStaticInspection)
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
}
