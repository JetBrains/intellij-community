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


class GrSpecificTest extends GrConstantConditionsTestBase {

  void "test range bounds"() {
    testHighlighting '''
import org.jetbrains.annotations.Nullable

def range0() {
    1..1
    <warning descr="Passing 'null' bound">null</warning>..1
    1..<warning descr="Passing 'null' bound">null</warning>
    <warning descr="Passing 'null' bound">null</warning>..<warning descr="Passing 'null' bound">null</warning>
    null..<null
}

def range1(@Nullable a) {
    if (a == null) {}
    if (a != null) {}
    <warning descr="Bound 'a' might be null">a</warning>..1
    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
}

def range2(@Nullable a) {
    if (a == null) {}
    if (a != null) {}
    2..<warning descr="Bound 'a' might be null">a</warning>
    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
}

def range3(@Nullable a) {
    if (a == null) {}
    if (a != null) {}
    <warning descr="Bound 'a' might be null">a</warning>..<warning descr="Bound 'a' might be null">a</warning>
    if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
    if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
}

def range4(@Nullable a) {
    if (a == null) {}
    if (a != null) {}
    a..<a
    if (a == null) {}
    if (a != null) {}
}
'''
  }
}
