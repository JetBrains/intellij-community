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
def ifNullUnknown(o) {
    if (o == null) {
        <warning descr="Method invocation 'o.toString()' may produce 'java.lang.NullPointerException'">o.toString()</warning>
    } else {
        o.hashCode()
    }
}

def ifNotNullUnknown(o) {
    if (o != null) {
        if (!o) {
            o.toString()
        }
    } else {
        <warning descr="Method invocation 'o.hashCode()' may produce 'java.lang.NullPointerException'">o.hashCode()</warning>
    }
}
'''
  }

  void "test unknown value coercion"() {
    testHighlighting '''
def unknownConditions(a) {
    if (a) {
        a.toString()
        if (<warning descr="Condition 'a' is always true">a</warning>) {}
        if (<warning descr="Condition '!a' is always false">!a</warning>) {}
        if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
        if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
        if (a == "a") {}
    } else {
        if (<warning descr="Condition 'a' is always false">a</warning>) {}
        if (<warning descr="Condition '!a' is always true">!a</warning>) {}
        if (a == null) {}
        if (a != null) {}
        if (a == "a") {}
    }
}
'''
  }
  
  void "test asBoolean() override" () {
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
}
