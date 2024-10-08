// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.formatter

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.GroovyLanguage

/**
 * @author Max Medvedev
 */
class WrappingTest extends GroovyFormatterTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp()
    myTempSettings.setRightMargin(GroovyLanguage.INSTANCE, 10)
  }

  void testWrapChainedMethodCalls() {
    groovySettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    checkFormatting('''\
foo().barbar().abcd()
''', '''\
foo()
    .barbar()
    .abcd()
''')
  }

  void testWrapChainedMethodCallsWithDotAfter() {
    groovySettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    groovyCustomSettings.WRAP_CHAIN_CALLS_AFTER_DOT = true

    checkFormatting('''\
foo().barbar().abcd()
''', '''\
foo().
    barbar().
    abcd()
''')
  }

  void testWrappingInsideGString0() {
    checkFormatting('''\
"abcdefghij${a+b}"
''', '''\
"abcdefghij${a + b}"
''')
  }

  void testWrappingInsideGString1() {
    checkFormatting('''\
"""abcdefghij${a+b}"""
''', '''\
"""abcdefghij${a + b}"""
''')
  }

  void testWrappingInsideGString2() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    checkFormatting('''\
"""abcdefghij${a+b}"""
''', '''\
"""abcdefghij${
  a + b
}"""
''')
  }

  void testWrappingInsideGString3() {
    checkFormatting('''\
"""text with ${foo}"""
''', '''\
"""text with ${foo}"""
''')
  }

  void testWrappingInsideGString4() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    checkFormatting('''\
"""text with ${foooo}"""
''', '''\
"""text with ${
  foooo
}"""
''')
  }

  void testWrappingInsideGString5() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    checkFormatting('''\
"""text with ${foo}"""
''', '''\
"""text with ${
  foo}"""
''')
  }

  void testWrapArgumentList() {
    groovySettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    checkFormatting('''\
printxxxxxx(2)
''', '''\
printxxxxxx(
    2)
''')
  }

  void testWrapOneLineClosure() {
    checkFormatting('''\
"""def barbar = {foo}"""
''', '''\
"""def barbar = {foo}"""
''')
  }

  void testWrapOneLineClosure2() {
    groovySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false
    checkFormatting('''\
def barbarbar = {foo}
''', '''\
def barbarbar = {
  foo
}
''')
  }
}