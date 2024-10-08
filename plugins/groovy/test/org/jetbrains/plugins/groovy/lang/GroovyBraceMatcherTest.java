// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Test

@CompileStatic
class GroovyBraceMatcherTest extends GroovyLatestTest implements BaseTest {

  private void doTest(String before, String charsToType, String after) {
    configureByText(before)
    fixture.type(charsToType)
    fixture.checkResult(after)
  }

  @Test
  void 'left parenthesis'() {
    doTest 'foo<caret>', '(', 'foo(<caret>)' // before eof
    doTest 'foo<caret> ', '(', 'foo(<caret>) ' // before whitespace
    doTest 'foo<caret>\n', '(', 'foo(<caret>)\n'
    doTest 'foo<caret>)', '(', 'foo(<caret>)'
    doTest 'foo<caret>]', '(', 'foo(<caret>)]'
    doTest '[foo<caret>]', '(', '[foo(<caret>)]'
    doTest '[foo<caret>)', '(', '[foo(<caret>)'
    doTest '(foo<caret>]', '(', '(foo(<caret>)]'
    doTest '(foo<caret>)', '(', '(foo(<caret>))'
  }

  @Test
  void 'left bracket'() {
    doTest 'foo<caret>', '[', 'foo[<caret>]' // before eof
    doTest 'foo<caret> ', '[', 'foo[<caret>] ' // before whitespace
    doTest 'foo<caret>\n', '[', 'foo[<caret>]\n'
    doTest 'foo<caret>)', '[', 'foo[<caret>])'
    doTest 'foo<caret>]', '[', 'foo[<caret>]'
    doTest '[foo<caret>]', '[', '[foo[<caret>]]'
    doTest '[foo<caret>)', '[', '[foo[<caret>])'
    doTest '(foo<caret>]', '[', '(foo[<caret>]'
    doTest '(foo<caret>)', '[', '(foo[<caret>])'
  }
}
