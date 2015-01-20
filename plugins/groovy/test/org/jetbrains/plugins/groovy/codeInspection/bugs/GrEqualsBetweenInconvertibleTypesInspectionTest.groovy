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
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GrEqualsBetweenInconvertibleTypesInspectionTest extends GrHighlightingTestBase {

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    return new GrEqualsBetweenInconvertibleTypesInspection();
  }

  void test() {
    testHighlighting('''
class A {}
class B {}
class C extends B {}

def s = "1"
def i = 1
A a = new A()
B b = new B()
C c = new C()
    
<warning descr="'==' between objects of inconvertible types 'String' and 'Integer'">s == i</warning>
<warning descr="'==' between objects of inconvertible types 'Integer' and 'String'">i == s</warning>
<warning descr="'==' between objects of inconvertible types 'A' and 'B'">a == b</warning>
b == c
<warning descr="'==' between objects of inconvertible types 'C' and 'A'">c == a</warning>

s.<warning descr="'equals()' between objects of inconvertible types 'String' and 'Integer'">equals</warning>(i)
i.<warning descr="'equals()' between objects of inconvertible types 'Integer' and 'String'">equals</warning>(s)
a.<warning descr="'equals()' between objects of inconvertible types 'A' and 'B'">equals</warning>(b)
b.equals(c)
c.<warning descr="'equals()' between objects of inconvertible types 'C' and 'A'">equals</warning>(a)

s.<warning descr="'equals()' between objects of inconvertible types 'String' and 'Integer'">equals</warning> i
i.<warning descr="'equals()' between objects of inconvertible types 'Integer' and 'String'">equals</warning> s
a.<warning descr="'equals()' between objects of inconvertible types 'A' and 'B'">equals</warning> b
b.equals c
c.<warning descr="'equals()' between objects of inconvertible types 'C' and 'A'">equals</warning> a
''')
  }
}
