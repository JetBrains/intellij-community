// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.RecursionManager
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.Groovy30Test
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Before
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER

@CompileStatic
class UntypedParameterTest extends Groovy30Test implements TypingTest {

  @Before
  void disableRecursion() {
    RecursionManager.assertOnRecursionPrevention(fixture.testRootDisposable)
  }

  @Test
  void 'basic inference'() {
    typingTest '''
def foo(a) {
  <caret>a
}

foo(1)
''', JAVA_LANG_INTEGER
  }

}


