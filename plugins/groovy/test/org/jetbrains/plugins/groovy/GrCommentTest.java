package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class GrCommentTest extends GroovyFormatterTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "grComment/";
  }

  public void testUncommentLine() {
    lineTest("""
//<caret>print 2
""", """
print 2
<caret>""");
  }

  public void testBlock0() {
    blockTest("""
<selection>print 2</selection>
""", """
<selection>/*print 2*/</selection>
""");
  }

  public void testUncommentBlock0() {
    blockTest("""
<selection>/*print 2*/</selection>
""", """
<selection>print 2</selection>
""");
  }

  public void testBlock1() {
    blockTest("""
<selection>print 2
</selection>
""", """
<selection>/*
print 2
*/
</selection>
""");
  }

  public void testUncommentBlock1() {
    blockTest("""
<selection>/*
print 2
*/
</selection>
""", """
<selection>print 2
</selection>
""");
  }

  public void test_line_comment_no_indent() {
    lineTest("""
def foo() {
  <caret>print 2
}
""", """
def foo() {
//  print 2
}<caret>
""");
  }

  public void test_line_comment_indent() {
    getGroovySettings().LINE_COMMENT_AT_FIRST_COLUMN = false;
    lineTest("""
def foo() {
  println 42<caret>
}
""", """
def foo() {
  //println 42
}<caret>
""");
  }

  public void test_line_comment_indent_and_space() {
    getGroovySettings().LINE_COMMENT_AT_FIRST_COLUMN = false;
    getGroovySettings().LINE_COMMENT_ADD_SPACE = true;
    lineTest("""
def foo() {
  println 42<caret>
}
""", """
def foo() {
  // println 42
}<caret>
""");
  }

  public void lineTest(String before, String after) {
    doTest(before, after, new CommentByLineCommentAction());
  }

  public void blockTest(String before, String after) {
    doTest(before, after, new CommentByBlockCommentAction());
  }

  private void doTest(@NotNull String before, @NotNull String after, final AnAction action) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    myFixture.testAction(action);
    myFixture.checkResult(after);
  }
}
