/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.strings

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase
import org.jetbrains.plugins.groovy.intentions.conversions.strings.ConvertStringToMultilineIntention
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class ConvertStringToMultilineTest extends GrIntentionTestCase {
  ConvertStringToMultilineTest() {
    super(ConvertStringToMultilineIntention.getHint())
  }

  final String basePath = TestUtils.testDataPath + "intentions/convertToMultiline/"

  void testPlainString() {
    doTextTest("print 'ab<caret>c'", "print '''abc'''")
  }

  void testGString() {
    doTextTest('print "ab<caret>c"', 'print """abc"""')
  }

  void testPlainString2() {
    doTextTest("print 'a\\nb<caret>c'", "print '''a\nbc'''")
  }

  void testGString2() {
    doTextTest('print "a\\nb<caret>c"', 'print """a\nbc"""')
  }

  void testGString3() {
    doTextTest('print "a\\nb${a<caret>}c"', 'print """a\nb${a}c"""')
  }

  void testAlreadyMultiline() {
    doAntiTest('print """a<caret>bc"""')
  }

  void testGString4() {
    doTextTest('''\
print "ab<caret>c\\$ $x"
''', '''\
print """abc\\$ $x"""
''')
  }

  void doSelectionTest(String before, String after) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before)

    assertTrue myFixture.editor.selectionModel.hasSelection()
    final IntentionAction intention = new ConvertStringToMultilineIntention()
    assertTrue intention.isAvailable(myFixture.project, myFixture.editor, myFixture.file)
    intention.invoke(myFixture.project, myFixture.editor, myFixture.file)

    myFixture.checkResult(after)
  }

  void testSimpleConcatenation() {
    doSelectionTest("""
print <selection>'foo\\n' +
      'bar'</selection>
""", """
print <selection>'''foo
bar'''</selection>
""")
  }

  void testGStringConcatenation() {
    doSelectionTest('''
print <selection>"foo${x}\\n" +
      "bar"</selection>
''', '''
print <selection>"""foo${x}
bar"""</selection>
''')
  }
}
