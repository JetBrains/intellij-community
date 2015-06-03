/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting.constantConditions

import groovy.transform.CompileStatic

@CompileStatic
class GrConditionsTest extends GrConstantConditionsTestBase {

  void "test ternary expression"() {
    testHighlighting '''
import org.jetbrains.annotations.NotNull

@NotNull
def ternarySimple(a) {
    a == null ?
      <warning descr="Method invocation 'a.method()' may produce 'java.lang.NullPointerException'">a.method()</warning>
      : <warning descr="'null' is returned by the method declared as @NotNull">null</warning>
}

@NotNull
def ternary(a) {
    a ? (<warning descr="Condition 'a' is always true">a</warning> ? <warning descr="'null' is returned by the method declared as @NotNull">null</warning> : null)
      : <warning descr="Condition 'a' is always false">a</warning> ? null : <warning descr="'null' is returned by the method declared as @NotNull">null</warning>
}
'''
  }

  void "test null relations"() {
    testHighlighting '''
def ifNullUnknown(a) {
    if (a == null) {
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
    } else {
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    }
}

def ifNotNullUnknown(a) {
    if (a != null) {
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } else {
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
    }
}
'''
  }

  void "test unknown value coercion"() { doTest() }

  void "test asBoolean() override"() {
    testHighlighting '''
class OverrideBoolean {
    def asBoolean() { true }
}

def testOverrideBoolean(OverrideBoolean a) {
    if (a) {
        if (a) {}
        if (!a) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
    } else {
        if (a) {}
        if (!a) {}
        if (a == null) {}
        if (a != null) {}
    }
}
'''
  }

  void "test several states false condition"() {
    testHighlighting '''
def test(a) {
    def b, c
    if (a) {
        b = new Object()
        c = null
    } else {
        b = null
        c = new Object()
    }
    if (<warning descr="Condition 'b == null && c == null' is always false">b == null && <warning descr="Condition 'c == null' is always false">c == null</warning></warning>) {}
}
'''
  }

  void "test chained safe call" () {
    testHighlighting '''\
class A { B b }
class B { B b }

def test(A a) {
    def var = a?.b?.b?.b
    if (var != null) {
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a.b == null' is always false">a.b == null</warning>) {}
        if (<warning descr="Condition 'a.b.b == null' is always false">a.b.b == null</warning>) {}
        if (<warning descr="Condition 'a.b.b.b == null' is always false">a.b.b.b == null</warning>) {}
        if (a.b.b.b.b == null) {}
    }
}
'''
  }
  
  void "test constant value conditions"() { doTest() }

  void "test unknown value conditions"() { doTest() }

  void "test boolean variable conditions"() { doTest() }
}
