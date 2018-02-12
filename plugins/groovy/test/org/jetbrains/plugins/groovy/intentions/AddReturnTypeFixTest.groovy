// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions

import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class AddReturnTypeFixTest extends GrIntentionTestCase {
  AddReturnTypeFixTest() {
    super('Add return type')
  }

  final String basePath = TestUtils.testDataPath + 'intentions/addReturnType/'

  void testSimple() {
    doTextTest('def f<caret>oo() {}', 'void f<caret>oo() {}')
  }

  void testTypePrams() {
    doTextTest('def <T> f<caret>oo() {}', 'def <T> void f<caret>oo() {}')
  }

  void testReturnPrimitive() {
    doTextTest('def foo() {re<caret>turn 2}', 'int foo() {re<caret>turn 2}')
  }

  void testReturn() {
    doTextTest('def foo() {re<caret>turn "2"}', 'String foo() {re<caret>turn "2"}')
  }

  void 'test at the end of header range'() {
    doTextTest 'def f5()<caret> { def d = 10 }', 'void f5() { def d = 10 }'
  }
}
