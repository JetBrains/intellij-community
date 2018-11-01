// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ReferenceExpressionTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_META_CLASS

@CompileStatic
class MapPropertyTest extends GroovyLatestTest implements ReferenceExpressionTest {

  @Test
  void 'arbitrary property in instance context'() {
    referenceExpressionTest 'new HashMap<String, Integer>().foo', null, JAVA_LANG_INTEGER
  }

  @Test
  void 'arbitrary property in static context'() {
    referenceExpressionTest 'HashMap.foo', null, null
  }

  @Test
  void 'class property in instance context'() {
    referenceExpressionTest 'new HashMap<String, Integer>().class', null, JAVA_LANG_INTEGER
  }

  @Test
  void 'class property in raw instance context'() {
    referenceExpressionTest 'new HashMap().class', null, null
  }

  @Test
  void 'class property in static context'() {
    referenceExpressionTest 'HashMap.class', null, 'java.lang.Class<java.util.HashMap>'
  }

  @Test
  void 'metaClass property in instance context'() {
    referenceExpressionTest 'new HashMap().metaClass', null, null
  }

  @Test
  void 'metaClass property in static context'() {
    referenceExpressionTest 'HashMap.metaClass', GrGdkMethod, GROOVY_LANG_META_CLASS
  }

  @Test
  void 'class property on empty map literal'() {
    referenceExpressionTest '[:].class', null, null
  }

  @Test
  void 'class property on map literal'() {
    referenceExpressionTest '[class : 1].class', null, JAVA_LANG_INTEGER
  }
}
