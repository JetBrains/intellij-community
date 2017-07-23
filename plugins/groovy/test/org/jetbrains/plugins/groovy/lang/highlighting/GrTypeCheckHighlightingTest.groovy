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
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection

class GrTypeCheckHighlightingTest extends GrHighlightingTestBase {

  @Override
  String getBasePath() { return super.getBasePath() + 'typecheck/' }

  @Override
  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }

  void testTypeCheckClass() { doTest() }

  void testTypeCheckBool() { doTest() }

  void testTypeCheckChar() { doTest() }

  void testTypeCheckEnum() { doTest() }

  void testTypeCheckString() { doTest() }

  void testCastBuiltInTypes() { doTest() }

  void doTest() {
    addBigDecimal()
    addBigInteger()
    super.doTest()
  }

  void 'test box primitive types in list literals'() {
    testHighlighting '''
void method(List<Integer> ints) {}
void method2(Map<String, Integer> map) {}

interface X {
    int C = 0
    int D = 1
}

method([X.C, X.D])
method2([a: X.C, b: X.D])
'''
  }

  void 'test map without string keys and values'() {
    testHighlighting '''\
def foo(int a) {}
def m = [(aa): (<error descr="Expression expected">)</error>]
foo<warning descr="'foo' in '_' cannot be applied to '(java.util.LinkedHashMap)'">(m)</warning>
'''
  }

  void 'test assignment when getter and setter have different types'() {
    testHighlighting '''\
interface MavenArtifactRepository {
  URI getUrl()
  void setUrl(Object var1)
}
def test(MavenArtifactRepository m) {
  m.url = "String"
}
'''
  }
}
