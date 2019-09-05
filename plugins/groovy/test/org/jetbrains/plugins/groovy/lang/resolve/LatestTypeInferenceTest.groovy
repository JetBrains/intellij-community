// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

@CompileStatic
class LatestTypeInferenceTest extends GroovyLatestTest implements TypingTest {

  @Test
  void 'nested call with GString argument passed to String parameter'() {
    typingTest '''\
String foo(String s) { s }
foo("${42}").with { <caret>it }
''', GrReferenceExpression, JAVA_LANG_STRING
  }
}
