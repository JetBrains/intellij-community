/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.transformations

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase

import static org.jetbrains.plugins.groovy.util.ThrowingTransformation.disableTransformations

@CompileStatic
class GrNoTransformationsTest extends GroovyResolveTestCase {

  LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  @Override
  void setUp() {
    super.setUp()
    disableTransformations testRootDisposable
    addSomeClasses()
  }

  void 'test annotation resolve static import'() {
    resolveByText '''\
import static foo.bar.Hello.Foo
@Fo<caret>o
def foo() {}
''', null
  }

  void 'test annotation resolve static star import'() {
    resolveByText '''\
import static foo.bar.World.*
@Fo<caret>o
def foo() {}
''', null
  }

  void 'test resolve local in script'() {
    resolveByText '''\
import static foo.bar.Hello.*
def bar = 1
<caret>bar
''', GrVariable
  }

  void 'test resolve local from method in script'() {
    resolveByText '''\
import static foo.bar.Hello.*

def bar = 42

def foo() {
  def bar = 1
  <caret>bar
} 
''', GrVariable
  }

  void 'test resolve parameter from method in script'() {
    resolveByText '''\
import static foo.bar.World.*

def bar = 42

def foo(bar) {
  <caret>bar
}
''', GrParameter
  }

  void 'test resolve parameter from method in class'() {
    resolveByText '''\
import static foo.bar.World.*

def bar = 42

class M {
  def bar
  def foo(bar) {
    <caret>bar
  }
}
''', GrParameter
  }

  void 'test resolve local from inside anonymous'() {
    resolveByText '''\
import static foo.bar.Hello.*

def foo() {
  def bar = 1
  new Runnable() {
    void run() {
      <caret>bar
    }
  }
}
''', GrVariable
  }

  void 'test resolve parameter from inside anonymous'() {
    resolveByText '''\
import static foo.bar.Hello.*

def foo(bar) {
  new Runnable() {
    void run() {
      <caret>bar
    }
  }
}
''', GrParameter
  }

  private void addSomeClasses() {
    fixture.addFileToProject 'foo/bar/classes.groovy', '''\
package foo.bar
class Hello {}
class World {}
'''
  }
}
