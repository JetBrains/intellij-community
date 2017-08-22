/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
/**
 * @author Max Medvedev
 */
class GrAssignabilityClosureToSamTest extends GrHighlightingTestBase {
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() { GroovyLightProjectDescriptor.GROOVY_2_2 }

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
