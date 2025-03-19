package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.unwrap.UnwrapHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class GroovyUnwrapTest extends LightJavaCodeInsightFixtureTestCase {
  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    myFixture.configureByText("A.groovy", codeBefore);
    new UnwrapHandler().invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    myFixture.checkResult(expectedCodeAfter);
  }

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

  public void testUnwrapFor1() {
    assertUnwrapped("""
                      for(int i = 0; i < 10; i++) {
                          Sys<caret>tem.gc();
                      }
                      """, "Sys<caret>tem.gc();");
  }

  public void testBraces() throws Exception {
    assertUnwrapped("""
<caret>{
  def x = 1
}
""", "def x = 1");
  }

  public void testUnwrapParameterUnderArgumentList() {
    assertUnwrapped("xxx(1, yyy(<caret>1), 2)", "xxx(1, <caret>1, 2)");
  }

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

  public void testConditionalThat() {
    assertUnwrapped("xxx(f ? <caret>'1' : '2');\n", "xxx('1');\n");
  }

  public void testConditionalElse() {
    assertUnwrapped("xxx(f ? '1' : '2' +<caret> 3);\n", "xxx('2' +<caret> 3);\n");
  }

  public void testConditionalFromParameterList2() {
    assertUnwrapped("xxx(11, f ? '1' : '2' +<caret> 3, 12);\n", "xxx(11, '2' +<caret> 3, 12);\n");
  }

  public void testConditionalCond1() {
    assertUnwrapped("f <caret>? \"1\" : \"2\" + 3", "\"1\"");
  }

  public void testConditionalCond2() {
    assertUnwrapped("<caret>f ? \"1\" : \"2\" + 3", "\"1\"");
  }

  public void testConditionalUnwrapUnderAssigmentExpression() {
    assertUnwrapped("String s = f ? \"1<caret>\" : \"2\";\n", "String s = \"1\";\n");
  }
}
