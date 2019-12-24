// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions.strings

import com.intellij.testFramework.RunAll
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.util.ActionTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Test

@CompileStatic
class ConvertStringIntentionTest extends GroovyLatestTest implements ActionTest {

  @Test
  void 'convert single-quoted to dollar-slashy'() {
    def data = [
      '/'             : '/',
      '$'             : '$$',
      'hello $ world' : 'hello $ world',
      'hello / world' : 'hello / world',
      'hello $/ world': 'hello $$/ world',
      'hello $$ world': 'hello $$$ world',
      'hello /$ world': 'hello $/$ world'
    ]
    def all = new RunAll()
    for (entry in data) {
      all = all.append {
        doActionTest(
          GroovyIntentionsBundle.message("convert.to.dollar.slash.regex.intention.name"),
          "print(<caret>'$entry.key')",
          "print(<caret>\$/$entry.value/\$)"
        )
      }
    }
    all.run()
  }

  @Test
  void 'convert slashy to dollar-slashy'() {
    def data = [
      '\\/'             : '/',
      '$'               : '$$',
      'hello $ world'   : 'hello $ world',
      'hello \\/ world' : 'hello / world',
      'hello $\\/ world': 'hello $$/ world',
      'hello $$ world'  : 'hello $$$ world',
      'hello \\/$ world': 'hello $/$ world'
    ]
    def all = new RunAll()
    for (entry in data) {
      all = all.append {
        doActionTest(
          GroovyIntentionsBundle.message("convert.to.dollar.slash.regex.intention.name"),
          "print(<caret>/$entry.key/)",
          "print(<caret>\$/$entry.value/\$)"
        )
      }
    }
    all.run()
  }
}
