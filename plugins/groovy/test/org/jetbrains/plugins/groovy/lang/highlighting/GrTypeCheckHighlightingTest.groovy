// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.RecursionManager
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
def m = [(aa): (<error descr="<expression> expected, got ')'">)</error>]
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

  void 'test parameter with single-character string initializer'() {
    testHighlighting "@groovy.transform.CompileStatic def ff(char c = '\\n') {}"
  }

  void 'test ambiguous call @CS'() {
    testHighlighting '''\
def ff(String s, Object o) {}
def ff(Object o, String s) {}

@groovy.transform.CompileStatic
def usage() {
  ff<error descr="Method call is ambiguous">("", "")</error>
}
'''
  }

  void 'test no warning for type parameter assigning'() {
    testHighlighting '''\
class A<T> {
    
    T value
    def foo(it) {
        value = it
    }
    
}
'''
  }
}
