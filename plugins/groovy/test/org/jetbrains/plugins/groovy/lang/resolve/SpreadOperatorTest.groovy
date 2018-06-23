// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.EdtRule
import org.jetbrains.plugins.groovy.util.FixtureRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.rules.TestRule

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_ARRAY_LIST
import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType

@CompileStatic
class SpreadOperatorTest implements BaseTest {

  public final FixtureRule myFixtureRule = new FixtureRule(GroovyProjectDescriptors.GROOVY_3_0, '')
  public final TestName myNameRule = new TestName()
  public final @Rule TestRule myRules = RuleChain.outerRule(myNameRule).around(myFixtureRule).around(new EdtRule())

  CodeInsightTestFixture getFixture() {
    myFixtureRule.fixture
  }

  @Test
  void 'spread property'() {
    def expression = (GrReferenceExpression)configureByExpression("[[['a']]]*.bytes")
    def results = expression.multiResolve(false)
    assert results.size() == 1
    assert results[0].spreadState != null
    assertType("$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<byte[]>>>", expression.type)
  }

  @Test
  void 'implicit spread property'() {
    def expression = (GrReferenceExpression)configureByExpression("[[['a']]].bytes")
    def results = expression.multiResolve(false)
    assert results.size() == 1
    assert results[0].spreadState != null
    assertType("$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<byte[]>>>", expression.type)
  }

  @Test
  void 'spread method'() {
    def expression = (GrMethodCall)configureByExpression("[[['a']]]*.getBytes()")
    def results = expression.multiResolve(false)
    assert results.size() == 0
    assertType(null, expression.type)
  }

  @Test
  void 'implicit spread method'() {
    def expression = (GrMethodCall)configureByExpression("[[['a']]].getBytes()")
    def results = expression.multiResolve(false)
    assert results.size() == 0
    assertType(null, expression.type)
  }
}
