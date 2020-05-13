// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.util.Groovy30Test
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

@CompileStatic
class ResolveLocalTest extends Groovy30Test implements ResolveTest {

  @Test
  void 'resource variable from try block'() {
    resolveTest 'try (def a) { println <caret>a }', GrVariable
  }

  @Test
  void 'resource variable from catch block'() {
    resolveTest 'try (def a) {} catch(e) { println <caret>a }', null
  }

  @Test
  void 'resource variable from finally block'() {
    resolveTest 'try (def a) {} finally { println <caret>a }', null
  }

  @Test
  void 'resource variable from another resource'() {
    resolveTest 'try (def a; def b = <caret>a) {}', GrVariable
  }

  @Test
  void 'forward resource variable'() {
    resolveTest 'try (def b = <caret>a; def a) {}', null
  }

  @Test
  void 'parameter from resource initializer'() {
    resolveTest 'def foo(param) { try (def a = <caret>param) {} }', GrParameter
  }

  @Test
  void 'for variable from for block'() {
    resolveTest 'for (def e,f;;){ <caret>e }', GrVariable
    resolveTest 'for (def e,f;;){ <caret>f }', GrVariable
    resolveTest 'for (def (e,f);;){ <caret>e }', GrVariable
    resolveTest 'for (def (e,f);;){ <caret>f }', GrVariable
  }

  @Test
  void 'for variable from for condition'() {
    resolveTest 'for (def e,f; <caret>e;){}', GrVariable
    resolveTest 'for (def e,f; <caret>f;){}', GrVariable
    resolveTest 'for (def (e,f); <caret>e;){}', GrVariable
    resolveTest 'for (def (e,f); <caret>f;){}', GrVariable
  }

  @Test
  void 'for variable from for update'() {
    resolveTest 'for (def e,f;; <caret>e){}', GrVariable
    resolveTest 'for (def e,f;; <caret>f){}', GrVariable
    resolveTest 'for (def (e,f);; <caret>e){}', GrVariable
    resolveTest 'for (def (e,f);; <caret>f){}', GrVariable
  }

  @Test
  void 'for variable from another variable'() {
    resolveTest 'for (def e,f = <caret>e;;) {}', GrVariable
    resolveTest 'for (def f = <caret>e, e;;) {}', null
  }

  @Test
  void 'for variable from for-each expression'() {
    resolveTest 'for (e : <caret>e) {}', null
    resolveTest 'for (e in <caret>e) {}', null
  }

  @Test
  void 'for variable from for-each block'() {
    resolveTest 'for (e : b) { <caret>e }', GrVariable
    resolveTest 'for (e in b) { <caret>e }', GrVariable
  }

  @Test
  void 'for variable after for'() {
    resolveTest 'for (def e;;) {}; <caret>e', null
    resolveTest 'for (e : b) {}; <caret>e', null
    resolveTest 'for (e in b) {}; <caret>e', null
  }

  @Test
  void 'lambda parameter'() {
    resolveTest 'def l = a -> <caret>a ', GrParameter
    resolveTest 'def l = (a) -> <caret>a ', GrParameter
    resolveTest 'def l = (Integer a, def b) -> {a; <caret>b} ', GrParameter
    resolveTest 'def l = a -> {<caret>a} ', GrParameter
  }

  @Test
  void 'variable after lambda'() {
    resolveTest 'def l = a -> a; <caret>a ', null
    resolveTest 'def l = (a) -> a; <caret>a ', null
    resolveTest 'def l = (Integer a) -> {}; <caret>a ', null
    resolveTest 'def l = a -> {}; <caret>a ', null
  }

  @Test
  void 'outer parameter in lambda'() {
    resolveTest 'def foo(param) { def l = a -> <caret>param }', GrParameter
    resolveTest 'def foo(param) { def l = a -> {<caret>param} }', GrParameter
    resolveTest 'def foo(param) { def l = (a = <caret>param) -> <caret>param }', GrParameter
  }

  @Test
  void 'local variable inside lambda'() {
    resolveTest 'def l = a -> {def param; <caret>param }', GrVariable
  }
}
