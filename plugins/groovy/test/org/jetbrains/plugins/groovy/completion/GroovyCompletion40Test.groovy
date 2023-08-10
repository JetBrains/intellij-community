// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class GroovyCompletion40Test extends GroovyCompletionTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK

  @Override
  protected void tearDown() {
    CodeInsightSettings.instance.completionCaseSensitive = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.instance.AUTOCOMPLETE_ON_CODE_COMPLETION = true
    super.tearDown()
  }

  void "test basic"() {
    doBasicTest('''\
def x = sw<caret>
''', '''\
def x = switch (<caret>)
''')
  }

  void "test case inside switch expression"() {
    doBasicTest('''\
def x = switch (10) {
  ca<caret>
}
''', '''\
def x = switch (10) {
  case <caret>
}
''')
  }

  void "test in switch block"() {
    doBasicTest("""
def x = switch (10) {
  case 10 -> {
    yie<caret>
  }
}""", """
def x = switch (10) {
  case 10 -> {
    yield <caret>
  }
}""")
  }
}
