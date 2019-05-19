// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMapProperty
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ExpressionTest
import org.junit.Before
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER

@CompileStatic
class CustomMapPropertyTest extends GroovyLatestTest implements ExpressionTest {

  @Before
  void addClasses() {
    fixture.addFileToProject 'classes.groovy', '''\
class Pojo { def pojoProperty }

class SomeMapClass extends HashMap<String, Pojo> {
    public static final CONSTANT = 1
    static class Inner {
        public static final INNER_CONSTANT = 4
    }
}
'''
  }

  @Test
  void 'constant in instance context'() {
    referenceExpressionTest 'new SomeMapClass().CONSTANT', GroovyMapProperty, 'Pojo'
  }

  @Test
  void 'constant in static context'() {
    referenceExpressionTest 'SomeMapClass.CONSTANT', GrField, JAVA_LANG_INTEGER
  }

  @Test
  void 'inner class in instance context'() {
    referenceExpressionTest 'new SomeMapClass().Inner', GroovyMapProperty, 'Pojo'
  }

  @Test
  void 'inner class in static context'() {
    referenceExpressionTest 'SomeMapClass.Inner', GrClassDefinition, 'java.lang.Class<SomeMapClass.Inner>'
  }

  @Test
  void 'inner property in instance context'() {
    resolveTest 'new SomeMapClass().Inner.<caret>pojoProperty', GrAccessorMethod
  }

  @Test
  void 'inner property in static context'() {
    resolveTest 'SomeMapClass.Inner.<caret>INNER_CONSTANT', GrField
  }
}
