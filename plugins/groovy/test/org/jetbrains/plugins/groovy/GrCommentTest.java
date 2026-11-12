package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixtureKt.groovyFormatterFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrCommentTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyFormatterFixture> formatter =
    groovyFormatterFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "grComment/");

  @Test
  public void testUncommentLine() {
    lineTest("""
//<caret>print 2
""", """
print 2
<caret>""");
  }

  @Test
  public void testBlock0() {
    blockTest("""
<selection>print 2</selection>
""", """
<selection>/*print 2*/</selection>
""");
  }

  @Test
  public void testUncommentBlock0() {
    blockTest("""
<selection>/*print 2*/</selection>
""", """
<selection>print 2</selection>
""");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void test_line_comment_indent() {
    formatter.get().getGroovySettings().LINE_COMMENT_AT_FIRST_COLUMN = false;
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

  @Test
  public void test_line_comment_indent_and_space() {
    formatter.get().getGroovySettings().LINE_COMMENT_AT_FIRST_COLUMN = false;
    formatter.get().getGroovySettings().LINE_COMMENT_ADD_SPACE = true;
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
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.testAction(action);
      myFixture.checkResult(after);
    });
  }
}
