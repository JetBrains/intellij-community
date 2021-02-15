// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import org.jetbrains.plugins.groovy.util.Groovy30Test
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_NUMBER
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

class TupleMultiAssignmentTypingTest extends Groovy30Test implements TypingTest {

  @Test
  void 'type of component is used in multi-declaration'() {
    expressionTypeTest '''\
Tuple2<String, Number> tuple() {}
def (s) = tuple()
s
''', JAVA_LANG_STRING
  }

  @Test
  void 'type of component is used in multi-declaration 2'() {
    expressionTypeTest '''\
Tuple2<String, Number> tuple() {}
def (s, n) = tuple()
n
''', JAVA_LANG_NUMBER
  }

  @Test
  void 'type of component is used in multi-assignment'() {
    expressionTypeTest '''\
Tuple2<String, Number> tuple() {}
def s
(s) = tuple()
s
''', JAVA_LANG_STRING
  }

  @Test
  void 'type of component is used in multi-assignment 2'() {
    expressionTypeTest '''\
Tuple2<String, Number> tuple() {}
def s,n
(s, n) = tuple()
n
''', JAVA_LANG_NUMBER
  }
}
