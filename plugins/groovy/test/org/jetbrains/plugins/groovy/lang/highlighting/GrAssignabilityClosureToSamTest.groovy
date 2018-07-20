// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection

/**
 * @author Max Medvedev
 */
class GrAssignabilityClosureToSamTest extends GrHighlightingTestBase {
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_2

  void testAssignability() {
    testHighlighting('''\
interface A {
  def foo()
}
interface B {}

A a = {print 1}
A a1 = {->print 1}
A a2 = {String s->print 1}
B <warning>b</warning> = {print 2}
''')
  }

  void testAmbiguous() {
    testHighlighting('''\
interface SAM1 { def foo(String s)}
interface SAM2 { def bar(Integer i)}

def method(x, SAM1 s1){s1.foo<weak_warning>(x)</weak_warning>}
def method(x, SAM2 s2){s2.bar<weak_warning>(x)</weak_warning>}

method <warning>(1)</warning>   {it}  // fails because SAM1 and SAM2 are seen as equal
method <warning>("1")</warning> {it}  // fails because SAM1 and SAM2 are seen as equal

''')
  }

  void testGenerics() {
    testHighlighting('''
interface A<T> {
  def foo(T t)
}

A<String> a1 = {print 1}
A<String> a2 = {String s -> print 1}
A<String> a3 = {int s -> print 1}
A a4 = {int s -> print 1}
A a7 = {int s, String y -> print 1}
A a5 = { print 1}
A a6 = {x -> print 1}
''')
  }

  void testGenerics2() {
    testHighlighting('''
interface A<T> {
  def foo(T t)
}

interface B extends A<String> {}

B b1 = {print 1}
B b2 = {String s -> print 1}
B b3 = {int s -> print 1}
''')
  }

}
