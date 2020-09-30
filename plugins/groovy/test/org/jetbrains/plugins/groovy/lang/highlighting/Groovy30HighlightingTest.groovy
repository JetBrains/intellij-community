// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.GroovyVersionBasedTest
import org.jetbrains.plugins.groovy.util.TestUtils

@CompileStatic
class Groovy30HighlightingTest extends GroovyVersionBasedTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0_REAL_JDK
  final String basePath = TestUtils.testDataPath + 'highlighting/v30/'

  void 'test default method in interfaces'() {
    highlightingTest '''
import groovy.transform.CompileStatic

interface I {
    default int bar() {
        2
    }
}

@CompileStatic
interface I2 {
    default int bar() {
        2
    }
}
'''
  }

  void 'test default modifier'() {
    highlightingTest '''
default interface I {
}

trait T {
    <warning descr="Modifier 'default' makes sense only in interface's methods">default</warning> int bar() {
        2
    }
}

class C {
    <warning descr="Modifier 'default' makes sense only in interface's methods">default</warning> int bar() {
        2
    }
}
'''
  }

  void 'test sam with default modifier'() {
    highlightingTest '''
interface I {
    int foo() 
    default int bar() {
        2
    }
}

I i = {3}
'''
  }

  void 'test method reference to SAM conversion'() {
    highlightingTest '''

class A {
  def String m(){

  }
}
List<A> list = []

list.sort(Comparator.comparing(A::m))
''', GroovyAssignabilityCheckInspection
  }

  void 'test method reference to SAM conversion 2'() {
    highlightingTest '''

class A {
  def String m(){

  }
}
List<A> list = []
def c = A::m
list.sort(Comparator.comparing(c))
''', GroovyAssignabilityCheckInspection
  }

  void 'test method reference to SAM conversion with overload'() {
    highlightingTest '''
class A {
  String m(Integer i){
    return null
  }
  
  Integer m(Thread i){
    return null
  }
}

interface SAM<T> {
  T m(Integer a);
}

def <T> T foo(SAM<T> sam) {
}

def a = new A()
foo(a::m).toUpperCase()
''', GrUnresolvedAccessInspection
  }

  void 'test constructor reference static access'() {
    fileHighlightingTest GrUnresolvedAccessInspection
  }

  void 'test illegal single argument lambda'() {
    fileHighlightingTest ()
  }

  void 'test type use in annotation description'() {
    fileHighlightingTest()
  }
}
//
//class A {
//  def String m(){
//
//  }
//}
//List<A> list = []
//
//list.sort(Comparator.comparing(A::m))
