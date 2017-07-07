/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    doTextTest('def f<caret>oo() {}', 'def void f<caret>oo() {}')
  }

  void testTypePrams() {
    doTextTest('def <T> f<caret>oo() {}', 'def <T> void f<caret>oo() {}')
  }

  void testReturnPrimitive() {
    doTextTest('def foo() {re<caret>turn 2}', 'def int foo() {re<caret>turn 2}')
  }

  void testReturn() {
    doTextTest('def foo() {re<caret>turn "2"}', 'def String foo() {re<caret>turn "2"}')
  }

  void 'test at the end of header range'() {
    doTextTest 'def f5()<caret> { def d = 10 }', 'def void f5() { def d = 10 }'
  }
}
