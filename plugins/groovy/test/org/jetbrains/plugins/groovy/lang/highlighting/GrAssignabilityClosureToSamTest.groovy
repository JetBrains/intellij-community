// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter

/**
 * @author Max Medvedev
 */
class GrAssignabilityClosureToSamTest extends GrHighlightingTestBase {
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_2

  void testAssignability() {
    doTestHighlighting('''\
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
    def registryValue = Registry.is(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE)
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true)
    try {
      doTestHighlighting('''\
interface SAM1 { def foo(String s)}
interface SAM2 { def bar(Integer i)}

def method(x, SAM1 s1){s1.foo(x)}
def method(x, SAM2 s2){s2.bar(x)}

method <warning>(1)</warning>   {it}  // fails because SAM1 and SAM2 are seen as equal
method <warning>("1")</warning> {it}  // fails because SAM1 and SAM2 are seen as equal

''')
    } finally {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(registryValue)
    }
  }

  void testGenerics() {
    doTestHighlighting('''
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
    doTestHighlighting('''
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
