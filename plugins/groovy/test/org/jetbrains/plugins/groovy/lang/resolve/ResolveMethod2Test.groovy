// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

@CompileStatic
class ResolveMethod2Test extends GroovyLatestTest implements ResolveTest {

  @Test
  void 'void argument'() {
    def result = advancedResolveByText 'def foo(p); void bar(); <caret>foo(bar())'
    assert result instanceof GroovyMethodResult
    assert ((GroovyMethodResult)result).applicability == Applicability.applicable
  }
}
