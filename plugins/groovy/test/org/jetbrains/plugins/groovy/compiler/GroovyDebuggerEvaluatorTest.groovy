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
              "a",
              "[a:a] -> a"
  }

  public void testVariableInsideClosure() throws Exception {
    evaluates """def a = 2
Closure c = { a++; <caret>a }
c()
a++""",
              "a",
              "[a:this.a] -> a"
  }

  public void testQualifyClassNames() throws Exception {
    myFixture.addFileToProject("com/Foo.groovy", """
package com
class Foo { static bar = 2 }""")

    def bar = myFixture.addFileToProject("com/Bar.groovy", """
package com
<caret>println 2""")

    myFixture.configureFromExistingVirtualFile bar.virtualFile

    evaluates "Foo.bar", "[:] -> com.Foo.bar"
  }

  private void evaluates(String text, String expression, String expected) throws IOException {
    myFixture.configureByText("_.groovy", text);
    evaluates(expression, expected)
  }

  private def evaluates(String expression, String expected) {
    def context = myFixture.file.findElementAt(myFixture.editor.caretModel.offset);
    def pair = GroovyCodeFragmentFactory.externalParameters(expression, context)

    assertEquals expected, pair.first.toString() + " -> " + pair.second.text
  }

  @Override
  protected String getBasePath() {
    return "";
  }
}
