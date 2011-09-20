package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInsight.unwrap.UnwrapHandler
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Sergey Evdokimov
 */
class GroovyUnwrapTest extends LightCodeInsightFixtureTestCase {

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    myFixture.configureByText("A.groovy", codeBefore);

    UnwrapHandler h = new UnwrapHandler() {
      @Override
      protected void selectOption(List<AnAction> options, Editor editor, PsiFile file) {
        if (options.isEmpty()) return;
        options.get(0).actionPerformed(null);
      }
    };

    h.invoke(project, myFixture.editor, myFixture.file);

    myFixture.checkResult(expectedCodeAfter)
  }


  public void testUnwrapIf() {
    assertUnwrapped("""
if (true) {
  a=1;
    c = 3
  b=1;<caret>
}
""",
"""a=1;
c = 3
b=1;
""")
  }

  public void testUnwrapFor1() {
    assertUnwrapped("""
for(int i = 0; i < 10; i++) {
    Sys<caret>tem.gc();
}
""", "Sys<caret>tem.gc();\n");
  }

  public void testBraces() throws Exception {
    assertUnwrapped("""
<caret>{
  def x = 1
}
""", """
def x = 1
""");
  }

  public void testTryWithCatches() throws Exception {
    assertUnwrapped("try {\n" +
                    "    int i;<caret>\n" +
                    "} catch(RuntimeException e) {\n" +
                    "    int j;\n" +
                    "} catch(Exception e) {\n" +
                    "    int k;\n" +
                    "}\n",

                    "int i;\n");
  }

}
