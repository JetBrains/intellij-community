// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.Test

@CompileStatic
class GroovyPsiUtilTest extends GroovyLatestTest {

  @Test
  void 'application expression'() {
    def expressions = [
      "foo"             : false,
      "foo.ref"         : false,
      "foo(a)"          : false,
      "foo[a]"          : false,
      "foo a"           : true,
      "foo(a) ref"      : true,
      "foo(a).ref"      : false,
      "foo a ref c"     : true,
      "foo a ref(c)"    : true,
      "foo(a) ref(c)"   : true,
      "foo(a).ref(c)"   : false,
      "foo a ref[c]"    : true,
      "foo(a) ref[c]"   : true,
      "foo(a).ref[c]"   : false,
      "foo a ref[c] ref": true,
      "foo a ref[c] (a)": true,
      "foo a ref[c] {}" : true,
      "foo a ref(c) ref": true,
      "foo a ref(c)(c)" : true,
      "foo a ref(c)[c]" : true,
    ]
    def factory = GroovyPsiElementFactory.getInstance(fixture.project)
    TestUtils.runAll(expressions) { expressionText, isApplication ->
      def expression = factory.createExpressionFromText(expressionText)
      assert PsiUtilKt.isApplicationExpression(expression) == isApplication: expressionText
    }
  }
}
