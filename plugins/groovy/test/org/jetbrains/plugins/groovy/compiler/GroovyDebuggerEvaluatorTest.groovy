package org.jetbrains.plugins.groovy.compiler;

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiElement;
import groovy.lang.GroovyShell;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.debugger.GroovyCodeFragmentFactory;

import java.io.IOException;

/**
 * @author peter
 */
public class GroovyDebuggerEvaluatorTest extends LightGroovyTestCase {

  public void testSimpleVariable() throws Exception {
    evaluates """def a = 2
<caret>a++""",
              "a", "2"
  }

  private void evaluates(String text, String expression, String expected) throws IOException {
    myFixture.configureByText("_.groovy", text);
    def context = myFixture.file.findElementAt(myFixture.editor.caretModel.offset);
    def factory = CodeFragmentFactory.EXTENSION_POINT_NAME.findExtension(GroovyCodeFragmentFactory.class);
    def fragment = factory.createCodeFragment(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression), context, project);
    def children = fragment.children;
    def last = children[children.length - 1].text;

    final String fragmentText = fragment.getText();
    text = text.replace("<caret>", "\n${fragmentText[0..-last.size()-1]}return $last;\n")
    System.out.println(text);
    String result = new GroovyShell().evaluate(text).toString();
    assertEquals(expected, result);
  }

  @Override
  protected String getBasePath() {
    return "";
  }
}
