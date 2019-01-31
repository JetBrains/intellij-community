// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.util.Groovy25Test
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

@CompileStatic
class Groovy25ResolveTest extends Groovy25Test implements ResolveTest {

  @Test
  void 'tap'() {
    def expression = elementUnderCaret '''\
class A { def prop }
new A().tap { <caret>prop = 1 }
''', GrReferenceExpression
    def reference = expression.LValueReference
    assert reference != null
    def resolved = reference.resolve()
    assert resolved instanceof GrAccessorMethod
  }
}
