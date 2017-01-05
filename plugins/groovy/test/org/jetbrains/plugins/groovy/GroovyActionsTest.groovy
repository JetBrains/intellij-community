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
package org.jetbrains.plugins.groovy

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
class GroovyActionsTest extends LightCodeInsightFixtureTestCase {

  final String basePath = TestUtils.testDataPath + 'groovy/actions/'

  void testSelectWordBeforeMethod() {
    doTestForSelectWord 1
  }

  void testSWInGString() { doTestSelectWordUpTo 5 }

  void 'test select word in GString select line before injection'() {
    doTestForSelectWord 2, '''\
print """
asddf
as<caret>df $b sfsasdf
fdsas
"""
''', '''\
print """
asddf
<selection>as<caret>df $b sfsasdf</selection>
fdsas
"""
'''
  }

  void 'test select word in GString select line after injection'() {
    doTestForSelectWord 2, '''\
print """
asddf
asdf $b sfs<caret>asdf
fdsas
"""
''', '''\
print """
asddf
<selection>asdf $b sfs<caret>asdf</selection>
fdsas
"""
'''
  }

  void 'test select word in GString select line before end'() {
    doTestForSelectWord 2, '''\
print """
asddf
asdf $b sfsasdf
fd<caret>sas fsss"""
''', '''\
print """
asddf
asdf $b sfsasdf
<selection>fd<caret>sas fsss</selection>"""
'''
  }

  void testSWInGStringMultiline() { doTestSelectWordUpTo 4 }

  void testSWInGStringBegin() { doTestSelectWordUpTo 2 }

  void testSWInGStringEnd() { doTestSelectWordUpTo 2 }

  void testSWInParameterList() { doTestForSelectWord(3) }

  void testSWInArgLabel1() { doTestForSelectWord(2) }

  void testSWInArgLabel2() { doTestForSelectWord(2) }

  void testSWInArgLabel3() { doTestForSelectWord(2) }

  void testSWEscapesInString() {
    doTestForSelectWord 1,
      "String s = \"abc\\nd<caret>ef\"",
      "String s = \"abc\\n<selection>d<caret>ef</selection>\""
  }

  void testSWListLiteralArgument() {
    doTestForSelectWord 2,
"foo([a<caret>], b)",
"foo(<selection>[a<caret>]</selection>, b)"
  }

  void testSWMethodParametersBeforeQualifier() {
    doTestForSelectWord 2,
"a.fo<caret>o(b)",
"a.<selection>foo(b)</selection>"
  }

  void testSWInCodeBlock() { doTestForSelectWord 5 }

  void testElseBranch() {
    doTestForSelectWord (3, '''\
def foo() {
  if (a){
  }
  else <caret>{
  }
}
''', '''\
def foo() {
  if (a){
  }
<selection>  else <caret>{
  }
</selection>}
''')
  }

  void testBlocksOfCode() {
    doTestForSelectWord(8, '''\
this.allOptions = [:];
    confTag.option.each{ opt ->
      def value = opt.'@value';
      if (value == null) {
        value = opt.value ? opt.value[0].'@defaultName' : null;
      }
      this.allOptions[opt.'@name'] = value;
    }

    def moduleNode = confTag.mod<caret>ule[0] ;
    if (moduleNode != null && !"wholeProject".equals(this.allOptions['TEST_SEARCH_SCOPE'])) {
      this.moduleRef = JpsElementFactory.instance.createModuleReference(moduleNode.'@name');
    } else {
      this.moduleRef = null;
    }

    this.macroExpander = macroExpander;
''', '''\
this.allOptions = [:];
    confTag.option.each{ opt ->
      def value = opt.'@value';
      if (value == null) {
        value = opt.value ? opt.value[0].'@defaultName' : null;
      }
      this.allOptions[opt.'@name'] = value;
    }

<selection>    def moduleNode = confTag.mod<caret>ule[0] ;
    if (moduleNode != null && !"wholeProject".equals(this.allOptions['TEST_SEARCH_SCOPE'])) {
      this.moduleRef = JpsElementFactory.instance.createModuleReference(moduleNode.'@name');
    } else {
      this.moduleRef = null;
    }
</selection>
    this.macroExpander = macroExpander;
''')
  }

  void "test hippie completion in groovydoc"() {
    myFixture.configureByText 'a.groovy', '''
class A {

  /** long<caret>
  */
  void longName() {}
  void example() {}
}
'''
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION)
    assert myFixture.editor.document.text.contains('** longName\n')
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION)
    assert myFixture.editor.document.text.contains('** longName\n')
  }

  void "test hippie completion with hyphenated match"() {
    myFixture.configureByText 'a.groovy', '''
foo = [ helloWorld: 1, "hello-world": {
    hw<caret>
    f
  }
]'''
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION)
    assert myFixture.editor.document.text.contains(' hello-world\n')
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION)
    assert myFixture.editor.document.text.contains(' helloWorld\n')

    myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf(' f') + 2)
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION)
    assert myFixture.editor.document.text.contains(' foo\n')
  }

  void testSWforMemberWithDoc() {
    doTestForSelectWord(4, '''\
class A {
  /**
   * abc
   */
  def fo<caret>o() {}

  def bar(){}
}
''', '''\
class A {
<selection>  /**
   * abc
   */
  def fo<caret>o() {}
</selection>
  def bar(){}
}
''')
  }

  private void doTestForSelectWord(int count, String input, String expected) {
    myFixture.configureByText("a.groovy", input)
    selectWord(count)
    myFixture.checkResult(expected)
  }

  private void doTestForSelectWord(int count) {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    selectWord(count)
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  private void doTestSelectWordUpTo(int count) {
    def testName = getTestName(false)
    myFixture.configureByFile "${testName}_0.groovy"
    myFixture.editor.settings.camelWords = true
    count.times {
      performEditorAction IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET
      myFixture.checkResultByFile "${testName}_${it + 1}.groovy"
    }
  }

  private def selectWord(int count) {
    myFixture.editor.settings.camelWords = true
    for (int i = 0; i < count; i++) {
      performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
    }
  }

  private void performEditorAction(final String actionId) {
    myFixture.performEditorAction(actionId)
  }

}
