package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.unwrap.UnwrapHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyUnwrapTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.groovy", codeBefore);
    EdtTestUtil.runInEdtAndWait(() -> {
      new UnwrapHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      myFixture.checkResult(expectedCodeAfter);
    });
  }

  @Test
  public void testUnwrapIf() {
        assertUnwrapped("""
                          if (true) {
                            a=1;
                              c = 3
                            b=1;<caret>
                          }
                          """, """
    a=1;
    c = 3
    b=1;""");
  }

  @Test
  public void testUnwrapFor1() {
    assertUnwrapped("""
                      for(int i = 0; i < 10; i++) {
                          Sys<caret>tem.gc();
                      }
                      """, "Sys<caret>tem.gc();");
  }

  @Test
  public void testBraces() throws Exception {
        assertUnwrapped("""
    <caret>{
      def x = 1
    }
    """, "def x = 1");
  }

  @Test
  public void testUnwrapParameterUnderArgumentList() {
    assertUnwrapped("xxx(1, yyy(<caret>1), 2)", "xxx(1, <caret>1, 2)");
  }

  @Test
  public void testTryWithCatches() {
    assertUnwrapped("""
                      try {
                          int i;<caret>
                      } catch(RuntimeException e) {
                          int j;
                      } catch(Exception e) {
                          int k;
                      }""", "int i;");
  }

  @Test
  public void testConditionalThat() {
    assertUnwrapped("xxx(f ? <caret>'1' : '2');\n", "xxx('1');\n");
  }

  @Test
  public void testConditionalElse() {
    assertUnwrapped("xxx(f ? '1' : '2' +<caret> 3);\n", "xxx('2' +<caret> 3);\n");
  }

  @Test
  public void testConditionalFromParameterList2() {
    assertUnwrapped("xxx(11, f ? '1' : '2' +<caret> 3, 12);\n", "xxx(11, '2' +<caret> 3, 12);\n");
  }

  @Test
  public void testConditionalCond1() {
    assertUnwrapped("f <caret>? \"1\" : \"2\" + 3", "\"1\"");
  }

  @Test
  public void testConditionalCond2() {
    assertUnwrapped("<caret>f ? \"1\" : \"2\" + 3", "\"1\"");
  }

  @Test
  public void testConditionalUnwrapUnderAssigmentExpression() {
    assertUnwrapped("String s = f ? \"1<caret>\" : \"2\";\n", "String s = \"1\";\n");
  }
}
