// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.unused.defaultParameter

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class GrUnusedDefaultParameterInspectionTest /*extends LightGroovyTestCase*/ {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void 'test simple 0'() {
    testHighlighting '''\
def foo(a = 1, b = 2) {}
foo()
'''
  }

  void 'test simple 1'() {
    testHighlighting '''\
def foo(a = <warning descr="Default parameter is not used">1</warning>, b = 2) {}
foo(10)
'''
  }

  void 'test simple 2'() {
    testHighlighting '''\
def foo(a = <warning descr="Default parameter is not used">1</warning>, b = <warning descr="Default parameter is not used">2</warning>) {}
foo(10, 20)
'''
  }

  void 'test map arguments'() {
    testHighlighting '''\
def foo(a = <warning descr="Default parameter is not used">1</warning>, b = 2) {}
foo(a: 2, b: 3, c: 4)
'''
  }

  void 'test default parameter in the middle used'() {
    testHighlighting '''
def foo(a, b = 2, c) {}
foo(1, 2)
'''
  }

  void 'test default parameter in the middle unused'() {
    testHighlighting '''
def foo(a, b = <warning descr="Default parameter is not used">2</warning>, c) {}
foo(1, 2, 3)
'''
  }

  void 'test delegate 0'() {
    testHighlighting '''
class A {
    def foo(a = 1, b) { a + b }
}
class B {
    @Delegate
    A dd
}

new B().foo(1)
'''
  }

  void 'test delegate 1'() {
    testHighlighting '''
class A {
    def foo(a = <warning descr="Default parameter is not used">1</warning>, b) { a + b }
}
class B {
    @Delegate
    A dd
}

new B().foo(1, 2)
'''
  }

  void 'test super method child usage 1'() {
    testHighlighting '''\
class A {
  def foo(a, b = 2) { a + b }
}
class B extends A {}

new B().foo(1)
'''
  }

  void 'test super method child usage 2'() {
    testHighlighting '''\
class A {
  def foo(a, b = <warning descr="Default parameter is not used">2</warning>) { a + b }
}
class B extends A {}

new B().foo(1, 2)
'''
  }

  void 'test super method child usage 3'() {
    testHighlighting '''\
class A {
  def foo(a, b = <warning descr="Default parameter is not used">2</warning>) { a + b }
}
class B extends A {
  def foo(a, b) {}
}

new B().foo(1, 2)
'''
  }

  void 'test reflected method do not have super method'() {
    testHighlighting '''
class A {
  def foo(a, b) {} 
}

class B extends A {
  def foo(a, b = <warning descr="Default parameter is not used">2</warning>) {}
}

def test(A a) {
  a.foo(1, 2)
}
'''
  }

  void 'test reflected method has super method'() {
    testHighlighting '''
class A {
  def foo(a) {} 
}

class B extends A {
  def foo(a, b = 2) {}
}
'''
  }

  void 'test reflected constructor'() {
    testHighlighting '''
class A {
  A(a) {} 
}

class B extends A {
  B(a, b = <warning descr="Default parameter is not used">2</warning>) { super("") }
}
'''
  }

  void 'test override method with default parameters'() {
    testHighlighting '''\
class A {
  def foo(a, b = <warning descr="Default parameter is not used">2</warning>) {}
}
class B extends A {
  def foo(a, b = 2) {}
}
'''
  }

  private void testHighlighting(String text) {
//    fixture.with {
//      configureByText '_.groovy', text
//      enableInspections GrUnusedDefaultParameterInspection
//      checkHighlighting()
//    }
  }
}