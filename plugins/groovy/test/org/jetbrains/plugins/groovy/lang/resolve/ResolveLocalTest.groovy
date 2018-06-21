// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.util.ResolveTest

@CompileStatic
class ResolveLocalTest extends LightGroovyTestCase implements ResolveTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  void 'test resource variable from try block'() {
    resolveTest 'try (def a) { println <caret>a }', GrVariable
  }

  void 'test resource variable from catch block'() {
    resolveTest 'try (def a) {} catch(e) { println <caret>a }', null
  }

  void 'test resource variable from finally block'() {
    resolveTest 'try (def a) {} finally { println <caret>a }', GrVariable
  }

  void 'test resource variable from another resource'() {
    resolveTest 'try (def a; def b = <caret>a) {}', GrVariable
  }

  void 'test forward resource variable'() {
    resolveTest 'try (def b = <caret>a; def a) {}', null
  }

  void 'test parameter from resource initializer'() {
    resolveTest 'def foo(param) { try (def a = <caret>param) {} }', GrParameter
  }
}
