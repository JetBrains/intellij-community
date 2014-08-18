/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
    myTempSettings.setRightMargin(GroovyLanguage.INSTANCE, 10);
  }

  void testWrapChainedMethodCalls() {
    groovySettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

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
"""abcdefghij${
  a + b
}"""
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
}
