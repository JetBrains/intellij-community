// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_CHARACTER

@CompileStatic
class ExpressionTypeTest extends GroovyLatestTest implements TypingTest {

  @Test
  void 'ternary with primitive types'() {
    expressionTypeTest "char a = '1'; char b = '1'; c ? a : b", JAVA_LANG_CHARACTER
  }
}
