// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class GroovyActionsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/actions/");
  }

  @Test
  public void testSelectWordBeforeMethod() {
    doTestForSelectWord(1);
  }

  @Test
  public void testSWInGString() { doTestSelectWordUpTo(5); }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testSWInGStringMultiline() { doTestSelectWordUpTo(4); }

  @Test
  public void testSWInGStringBegin() { doTestSelectWordUpTo(2); }

  @Test
  public void testSWInGStringEnd() { doTestSelectWordUpTo(2); }

  @Test
  public void testSWInParameterList() { doTestForSelectWord(3); }

  @Test
  public void testSWInArgLabel1() { doTestForSelectWord(2); }

  @Test
  public void testSWInArgLabel2() { doTestForSelectWord(2); }

  @Test
  public void testSWInArgLabel3() { doTestForSelectWord(2); }

  @Test
  public void testSWEscapesInString() {
    doTestForSelectWord(1, "String s = \"abc\\nd<caret>ef\"", "String s = \"abc\\n<selection>d<caret>ef</selection>\"");
  }

  @Test
  public void testSWListLiteralArgument() {
    doTestForSelectWord(2, "foo([a<caret>], b)", "foo(<selection>[a<caret>]</selection>, b)");
  }

  @Test
  public void testSWMethodParametersBeforeQualifier() {
    doTestForSelectWord(2, "a.fo<caret>o(b)", "a.<selection>foo(b)</selection>");
  }

  @Test
  public void testSWInCodeBlock() { doTestForSelectWord(5); }

  @Test
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

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    if (!myFixture.getEditor().getDocument().getText().contains(s)) {
      fail("Missing '" + s + "' in editor document text: " + myFixture.getEditor().getDocument().getText());
    }
  }

  @Test
  public void test_hippie_completion_in_groovydoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void test_hippie_completion_with_hyphenated_match() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

    EdtTestUtil.runInEdtAndWait(
      () -> myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf(" f") + 2));
    performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION);
    assertEditorTextContains(" foo\n");
  }

  @Test
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

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", input);
    selectWord(count);
    myFixture.checkResult(expected);
  }

  private void doTestForSelectWord(int count) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    selectWord(count);
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }

  private void doTestSelectWordUpTo(int count) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    myFixture.configureByFile(testName + "_0.groovy");
    myFixture.getEditor().getSettings().setCamelWords(true);
    for (int i = 0; i < count; i++) {
      performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
      myFixture.checkResultByFile(testName + "_" + (i + 1) + ".groovy");
    }
  }

  private void selectWord(int count) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.getEditor().getSettings().setCamelWords(true);
    for (int i = 0; i < count; i++) {
      performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    }
  }

  private void performEditorAction(final String actionId) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.performEditorAction(actionId);
  }
}
