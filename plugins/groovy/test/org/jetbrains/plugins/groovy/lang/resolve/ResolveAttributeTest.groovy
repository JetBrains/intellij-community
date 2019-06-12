// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ExpressionTest
import org.junit.Before
import org.junit.Test

@CompileStatic
class ResolveAttributeTest extends GroovyLatestTest implements ExpressionTest {

  @Before
  void addClasses() {
    fixture.addFileToProject 'A.groovy', '''
class A {
  static staticProp = 'static prop'
  public static staticField = 'static field'
  
  def instanceProp = 'instance prop'
  public instanceField = 'instance field'
  
  def getAccessorOnly() { 'accessor only' } 
}
'''
  }

  @Test
  void 'instance field in instance context'() {
    resolveTest 'new A().@<caret>instanceField', GrField
  }

  @Test
  void 'instance property in instance context'() {
    def result = advancedResolveByText 'new A().@<caret>instanceProp'
    assert result.element instanceof GrField
    assert !result.accessible
    assert !result.validResult
  }

  @Test
  void 'dont resolve accessor'() {
    resolveTest 'new A().@<caret>accessorOnly', null
  }

  @Test
  void 'static field in static context'() {
    resolveTest 'A.@<caret>staticField', GrField
    resolveTest 'A.class.@<caret>staticField', GrField
  }

  @Test
  void 'static property in static context'() {
    resolveTest 'A.@<caret>staticProp', GrField
    resolveTest 'A.class.@<caret>staticProp', GrField
  }

  @Test
  void 'static property in instance context'() {
    resolveTest 'new A().@<caret>staticProp', GrField
  }

  @Test
  void 'instance property in static context'() {
    def result = advancedResolveByText 'A.@<caret>instanceProp'
    assert result.element instanceof GrField
    assert !result.staticsOK
    assert !result.validResult
  }

  @Test
  void 'attribute in call'() {
    resolveTest 'new A().@<caret>instanceField()', GrField
  }

  @Test
  void 'spread attribute'() {
    referenceExpressionTest '[new A()]*.@instanceField', GrField, 'java.util.ArrayList<java.lang.String>'
  }

  @Test
  void 'spread attribute deep'() {
    referenceExpressionTest '[[new A()]]*.@instanceField', null, 'java.util.List'
  }

  @Test
  void 'spread static attribute'() {
    referenceExpressionTest '[A]*.@staticField', GrField, 'java.util.ArrayList<java.lang.String>'
  }
}
