// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

class FromAbstractTypeMethodsTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void 'test stuff'() {
    fixture.with {
      enableInspections GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection
      addFileToProject 'closureparameterstyping/classes.groovy', '''\
package closureparameterstyping

class A {
  def fooA() {}
}
class B {
  def fooB() {}
}
interface SomeInterface {
  def interfaceMethod(A a, B b)
  def interfaceMethod2(String s)
}
abstract class SomeBaseAbstractClass {
  abstract void baseClassMethod1(B b, String s)
  abstract void baseClassMethod2(A a)
}
abstract class SomeAbstractClass1 implements SomeInterface {
  abstract abstractClassMethod(A a)
  def nonAbstractCLassMethod(B b) {}
}
abstract class SomeAbstractClass2 extends SomeBaseAbstractClass {
  abstract abstractClassMethod(B a)
  def nonAbstractCLassMethod(A b) {}
}
'''
      configureByText '_.groovy', '''\
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromAbstractTypeMethods
import closureparameterstyping.*

def fooInterface(@ClosureParams(value = FromAbstractTypeMethods, options = 'closureparameterstyping.SomeInterface') Closure c) {}
def fooAbstractClass(@ClosureParams(value = FromAbstractTypeMethods, options = 'closureparameterstyping.SomeBaseAbstractClass') Closure c) {}
def fooAbstractClass1(@ClosureParams(value = FromAbstractTypeMethods, options = 'closureparameterstyping.SomeAbstractClass1') Closure c) {}
def fooAbstractClass2(@ClosureParams(value = FromAbstractTypeMethods, options = 'closureparameterstyping.SomeAbstractClass2') Closure c) {}

@groovy.transform.CompileStatic t() {
  fooInterface { a, b -> a.fooA(); b.fooB() }
  fooInterface { a -> a.toUpperCase() }
  fooInterface { it.toUpperCase() }
  
  fooAbstractClass { a, b -> a.fooB(); b.toUpperCase() }
  fooAbstractClass { it.fooA() }
  fooAbstractClass { a -> a.fooA() }
  
  fooAbstractClass1 { a, b -> a.fooA(); b.fooB() }
  fooAbstractClass1 { String a -> a.toUpperCase() }
  fooAbstractClass1 { A a -> a.fooA() }
  fooAbstractClass1 { it.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>() }
  
  fooAbstractClass2 { a, b -> a.fooB(); b.toUpperCase() }
  fooAbstractClass2 { A a -> a.fooA() }
  fooAbstractClass2 { B a -> a.fooB() }
  fooAbstractClass2 { it.<error descr="Cannot resolve symbol 'fooA'">fooA</error>() }
}
'''
      checkHighlighting()
    }
  }
}
