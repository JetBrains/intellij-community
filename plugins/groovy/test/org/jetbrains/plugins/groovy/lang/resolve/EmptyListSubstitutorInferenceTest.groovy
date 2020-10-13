// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.RecursionManager
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@CompileStatic
class EmptyListSubstitutorInferenceTest extends GroovyLatestTest implements TypingTest, ResolveTest {

  @Before
  void disableRecursion() {
    RecursionManager.assertOnRecursionPrevention(fixture.testRootDisposable)
  }

  @Test
  void 'simple'() {
    typingTest('[<caret>]', GrListOrMap, 'java.util.ArrayList')
  }

  @Test
  void 'in untyped variable initializer'() {
    typingTest('def l = [<caret>]', GrListOrMap, 'java.util.ArrayList')
    typingTest('def l = []; <caret>l', GrReferenceExpression, 'java.util.ArrayList')
  }

  @Test
  void 'in raw variable initializer'() {
    typingTest('List l = [<caret>]', GrListOrMap, 'java.util.ArrayList')
    typingTest('List l = []; <caret>l', GrReferenceExpression, 'java.util.ArrayList')
  }

  @Test
  void 'in typed variable initializer'() {
    typingTest('List<Integer> l = [<caret>]', GrListOrMap, 'java.util.ArrayList<java.lang.Integer>')
    typingTest('List<Integer> l = []; <caret>l', GrReferenceExpression, 'java.util.ArrayList<java.lang.Integer>')
  }

  @Test
  void 'in wrongly typed variable initializer'() {
    typingTest('int l = [<caret>]', GrListOrMap, 'java.util.ArrayList')
  }

  @Test
  void 'receiver of DGM method'() {
    typingTest('[<caret>].each {}', GrListOrMap, 'java.util.ArrayList')
  }

  @Test
  void 'receiver of DGM method in untyped variable initializer'() {
    typingTest('def s = [<caret>].each {}', GrListOrMap, 'java.util.ArrayList')
  }

  @Test
  void 'receiver of DGM method in raw variable initializer'() {
    typingTest('List l = [<caret>].each {}', GrListOrMap, 'java.util.ArrayList')
  }

  @Test
  void 'receiver of DGM method in typed variable initializer'() {
    typingTest('List<Integer> l = [<caret>].each {}', GrListOrMap, 'java.util.ArrayList')
  }

  @Test
  void 'in argument of new expression'() {
    typingTest 'new ArrayList<Integer>([<caret>])', GrListOrMap, 'java.util.ArrayList<java.lang.Integer>'
  }

  @Test
  void 'in argument of diamond new expression'() {
    typingTest 'new ArrayList<Integer>(new ArrayList<>([<caret>]))', GrListOrMap, 'java.util.ArrayList<java.lang.Integer>'
  }

  @Test
  void 'in argument of nested diamond new expression in variable initializer'() {
    typingTest 'List<Integer> l = new ArrayList<>(new ArrayList<>([<caret>]))', GrListOrMap, 'java.util.ArrayList<java.lang.Integer>'
  }

  @Test
  void 'in argument of generic method call'() {
    typingTest('def <T> T id(T a) {a}; id([<caret>])', GrListOrMap, 'java.util.ArrayList')
    typingTest('def <T> T id(T a) {a}; <caret>id([])', GrMethodCall, 'java.util.ArrayList')
  }

  @Test
  void 'in argument of generic method call with argument'() {
    typingTest('def <T> List<T> add(List<T> l, T v) {}; add([<caret>], 1)', GrListOrMap, 'java.util.ArrayList<java.lang.Integer>')
    typingTest('def <T> List<T> add(List<T> l, T v) {}; <caret>add([], 1)', GrMethodCall, 'java.util.List<java.lang.Integer>')
  }

  @Ignore("Requires list literal inference from both arguments and context type")
  @Test
  void 'empty list literal from outer list literal'() {
    typingTest('List<List<Integer>> l = [[<caret>]]', GrListOrMap, 'java.util.List<java.lang.Integer>')
  }

  /**
   * This test is wrong and exists only to preserve behaviour
   * and should fail when 'empty list literal from outer list literal' will pass.
   */
  @Test
  void 'list literal with empty list literal'() {
    typingTest('List<List<Integer>> l = <caret>[[]]', GrListOrMap, 'java.util.ArrayList<java.util.ArrayList>')
    typingTest('List<List<Integer>> l = [[<caret>]]', GrListOrMap, 'java.util.ArrayList')
  }
}
