/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.WriteAction
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.intentions.conversions.strings.ConvertStringToMultilineIntention

/**
 * @author Max Medvedev
 */
public class ConvertStringToMultilineTest extends LightGroovyTestCase {
  final String basePath = ''

  void testPlainString() {
    doTest("print 'ab<caret>c'", "print '''abc'''")
  }

  void testGString() {
    doTest('print "ab<caret>c"', 'print """abc"""')
  }

  void testPlainString2() {
    doTest("print 'a\\nb<caret>c'", "print '''a\nbc'''")
  }

  void testGString2() {
    doTest('print "a\\nb<caret>c"', 'print """a\nbc"""')
  }

  void testGString3() {
    doTest('print "a\\nb${a<caret>}c"', 'print """a\nb${a}c"""')
  }

  void testAlreadyMultiline() {
    doTest('print """a<caret>bc"""', null)
  }

  private doTest(String before, @Nullable String after) {
    myFixture.with {
      configureByText('_.groovy', before)
      def intentions = filterAvailableIntentions(ConvertStringToMultilineIntention.hint)
      if (!after) {
        assertEmpty(intentions)
        return
      }

      assertOneElement(intentions)
      def accessToken = WriteAction.start()
      try {
        intentions[0].invoke(project, editor, file)
      }
      finally {
        accessToken.finish()
      }
      checkResult(after)
    }
  }
}
