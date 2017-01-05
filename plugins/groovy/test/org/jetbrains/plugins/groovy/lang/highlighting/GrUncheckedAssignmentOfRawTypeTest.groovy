/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection

/**
 * @author Max Medvedev
 */
class GrUncheckedAssignmentOfRawTypeTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()] as InspectionProfileEntry[]
  }

  void testRawMethodAccess() { doTest() }

  void testRawFieldAccess() { doTest() }

  void testRawArrayStyleAccess() { doTest() }

  void testRawArrayStyleAccessToMap() { doTest() }

  void testRawArrayStyleAccessToList() { doTest() }

  void testRawClosureReturnType() {
    testHighlighting('''\
class A<T> {
  A(T t) {this.t = t}

  T t
  def cl = {
    return t
  }
}


def a = new A(new Date())
Date d = <warning descr="Cannot assign 'Object' to 'Date'">a.cl()</warning>
''')
  }

}
