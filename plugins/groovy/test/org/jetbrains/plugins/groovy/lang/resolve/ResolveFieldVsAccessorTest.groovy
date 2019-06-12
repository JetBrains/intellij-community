// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

import static org.jetbrains.plugins.groovy.util.ThrowingTransformation.disableTransformations

@CompileStatic
class ResolveFieldVsAccessorTest extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void 'test implicit this'() {
    disableTransformations testRootDisposable
    resolveByText '''\
class A {
  def prop = "field"
  def getProp() { "getter" }

  def implicitThis() {
    <caret>prop
  }
''', GrField
  }

  void 'test explicit this'() {
    disableTransformations testRootDisposable
    resolveByText '''\
class A {
  def prop = "field"
  def getProp() { "getter" }

  def explicitThis() {
    this.<caret>prop
  }
}
''', GrField
  }

  void 'test qualified'() {
    def method = resolveByText '''\
class A {
  def prop = "field"
  def getProp() { "getter" }

  def qualifiedUsage() {
    new A().<caret>prop
  }
}
''', GrMethod
    assert method.name == 'getProp'
  }

  void 'test inner class'() {
    resolveByText '''\
class A {
  def prop = "field"
  def getProp() { "getter" }

  def innerClass() {
    new Runnable() {
      void run() {
        <caret>prop
      }
    }
  }
}
''', GrMethod
  }

  void 'test inner class explicit this'() {
    resolveByText '''\
class A {
  def prop = "field"
  def getProp() { "getter" }

  def innerExplicitThis = new Runnable() {
    void run() {
      println A.this.<caret>prop
    }
  }
}
''', GrMethod
  }

  void 'test inner vs outer'() {
    def method = resolveByText '''\
class A {
  def prop = "field"
  def getProp() { "getter" }

  def innerProperty = new Runnable() {
    def getProp() { "inner getter" }

    void run() {
      println <caret>prop
    }
  }  
''', GrMethod
    assert method.containingClass instanceof GrAnonymousClassDefinition
  }

  void 'test implicit super'() {
    resolveByText '''\
class A {
  def prop = "field"
  def getProp() { "getter" }
}

class B extends A {
  def implicitSuper() {
    <caret>prop
  }
}
''', GrMethod
  }

  void 'test explicit super'() {
    resolveByText '''\
class A {
  def prop = "field"
  def getProp() { "getter" }
}

class B extends A {
  def explicitSuper() {
    super.<caret>prop
  }
}
''', GrMethod
  }
}
