// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyActionsTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSelectWordBeforeMethod() {
    doTestForSelectWord(1);
  }

  public void testSWInGString() { doTestSelectWordUpTo(5); }

  public void test_select_word_in_GString_select_line_before_injection() {
    doTestForSelectWord(2, """
print ""\"
asddf
as<caret>df $b sfsasdf
fdsas
""\"
""", """
print ""\"
asddf
<selection>as<caret>df $b sfsasdf</selection>
fdsas
""\"
""");
  }

  public void test_select_word_in_GString_select_line_after_injection() {
    doTestForSelectWord(2, """
print ""\"
asddf
asdf $b sfs<caret>asdf
fdsas
""\"
""", """
print ""\"
asddf
<selection>asdf $b sfs<caret>asdf</selection>
fdsas
""\"
""");
  }

  public void test_select_word_in_GString_select_line_before_end() {
    doTestForSelectWord(2, """
print ""\"
asddf
asdf $b sfsasdf
fd<caret>sas fsss""\"
""", """
print ""\"
asddf
asdf $b sfsasdf
<selection>fd<caret>sas fsss</selection>""\"
""");
  }

  public void testSWInGStringMultiline() { doTestSelectWordUpTo(4); }

  public void testSWInGStringBegin() { doTestSelectWordUpTo(2); }

  public void testSWInGStringEnd() { doTestSelectWordUpTo(2); }

  public void testSWInParameterList() { doTestForSelectWord(3); }

  public void testSWInArgLabel1() { doTestForSelectWord(2); }

  public void testSWInArgLabel2() { doTestForSelectWord(2); }

  public void testSWInArgLabel3() { doTestForSelectWord(2); }

  public void testSWEscapesInString() {
    doTestForSelectWord(1, "String s = \"abc\\nd<caret>ef\"", "String s = \"abc\\n<selection>d<caret>ef</selection>\"");
  }

  public void testSWListLiteralArgument() {
    doTestForSelectWord(2, "foo([a<caret>], b)", "foo(<selection>[a<caret>]</selection>, b)");
  }

  public void testSWMethodParametersBeforeQualifier() {
    doTestForSelectWord(2, "a.fo<caret>o(b)", "a.<selection>foo(b)</selection>");
  }

  public void testSWInCodeBlock() { doTestForSelectWord(5); }

  public void testElseBranch() {
    doTestForSelectWord(3, """
def foo() {
  if (a){
  }
  else <caret>{
  }
}
""", """
def foo() {
  if (a){
  }
<selection>  else <caret>{
  }
</selection>}
""");
  }

  public void testBlocksOfCode() {
    doTestForSelectWord(8, """
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
""", """
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
""");
  }

  private void assertEditorTextContains(String s) {
    if (!myFixture.getEditor().getDocument().getText().contains(s)) {
      fail("Missing '" + s + "' in editor document text: " + myFixture.getEditor().getDocument().getText());
    }
  }

  public void test_hippie_completion_in_groovydoc() {
    String text = """
      class A {

        /** long<caret>
        */
        void longName() {}
        void example() {}
      }
      """;
    myFixture.configureByText("a.groovy", text);
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION);
    assertEditorTextContains("** longName\n");
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION);
    myFixture.checkResult(text);
  }

  public void test_hippie_completion_with_hyphenated_match() {
    myFixture.configureByText("a.groovy", """
      foo = [ helloWorld: 1, "hello-world": {
          hw<caret>
          f
        }
      ]""");
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION);
    assertEditorTextContains(" hello-world\n");
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION);
    assertEditorTextContains(" helloWorld\n");

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf(" f") + 2);
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION);
    assertEditorTextContains(" foo\n");
  }

  public void testSWforMemberWithDoc() {
    doTestForSelectWord(4, """
class A {
  /**
   * abc
   */
  def fo<caret>o() {}

  def bar(){}
}
""", """
class A {
<selection>  /**
   * abc
   */
  def fo<caret>o() {}
</selection>
  def bar(){}
}
""");
  }

  public void testMultiLineStingSelection() {
    doTestForSelectWord(2, """
         print '''
           abc cde
           xyz <caret>ahc
         '''
      """, """
   print '''
<selection>     abc cde
     xyz <caret>ahc
</selection>   '''
""");

    doTestForSelectWord(3, """
         print '''
           abc cde
           xyz <caret>ahc
         '''
      """, """
         print '''<selection>
           abc cde
           xyz <caret>ahc
         </selection>'''
      """);
  }

  private void doTestForSelectWord(int count, String input, String expected) {
    myFixture.configureByText("a.groovy", input);
    selectWord(count);
    myFixture.checkResult(expected);
  }

  private void doTestForSelectWord(int count) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    selectWord(count);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private void doTestSelectWordUpTo(int count) {
    final String testName = getTestName(false);
    myFixture.configureByFile(testName + "_0.groovy");
    myFixture.getEditor().getSettings().setCamelWords(true);
    for (int i = 0; i < count; i++) {
      performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
      myFixture.checkResultByFile(testName + "_" + (i + 1) + ".groovy");
    }
  }

  private void selectWord(int count) {
    myFixture.getEditor().getSettings().setCamelWords(true);
    for (int i = 0; i < count; i++) {
      performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    }
  }

  private void performEditorAction(final String actionId) {
    myFixture.performEditorAction(actionId);
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = TestUtils.getTestDataPath() + "groovy/actions/";
}
