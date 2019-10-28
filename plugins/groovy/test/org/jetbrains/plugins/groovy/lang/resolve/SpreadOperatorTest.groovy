// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_ARRAY_LIST
import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType

@CompileStatic
class SpreadOperatorTest extends GroovyLatestTest implements BaseTest {

  @Test
  void 'spread property'() {
    def expression = lastExpression("[[['a']]]*.bytes", GrReferenceExpression)
    def results = expression.multiResolve(false)
    assert results.size() == 1
    assert results[0].spreadState != null
    assertType("$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<byte[]>>>", expression.type)
  }

  @Test
  void 'implicit spread property'() {
    def expression = lastExpression("[[['a']]].bytes", GrReferenceExpression)
    def results = expression.multiResolve(false)
    assert results.size() == 1
    assert results[0].spreadState != null
    assertType("$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<byte[]>>>", expression.type)
  }

  @Test
  void 'spread method'() {
    def expression = lastExpression("[[['a']]]*.getBytes()", GrMethodCall)
    def results = expression.multiResolve(false)
    assert results.size() == 0
    assertType(null, expression.type)
  }

  @Test
  void 'implicit spread method'() {
    def expression = lastExpression("[[['a']]].getBytes()", GrMethodCall)
    def results = expression.multiResolve(false)
    assert results.size() == 0
    assertType(null, expression.type)
  }
}
