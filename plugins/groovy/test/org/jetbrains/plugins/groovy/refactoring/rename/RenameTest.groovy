package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ven
 */
public class RenameTest extends LightCodeInsightFixtureTestCase {

  public void testClosureIt() throws Throwable { doTest(); }
  public void testTo_getter() throws Throwable { doTest(); }
  public void testTo_prop() throws Throwable { doTest(); }
  public void testTo_setter() throws Throwable { doTest(); }
  public void testScriptMethod() throws Throwable { doTest(); }

  public void testParameterIsNotAUsageOfGroovyParameter() throws Exception {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
def foo(f) {
  // Parameter
  println 'Parameter' // also
  return <caret>f
}
""")
    def txt = "Just the Parameter word, which shouldn't be renamed"
    def txtFile = myFixture.addFileToProject("a.txt", txt)

    def parameter = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset).resolve()
    myFixture.renameElement(parameter, "newName", true, true)
    myFixture.checkResult """
def foo(newName) {
  // Parameter
  println 'Parameter' // also
  return <caret>newName
}
"""
    assertEquals txt, txtFile.text
  }

  public void doTest() throws Throwable {
    final String testFile = getTestName(true).replace('$', '/') + ".test";
    final List<String> list = TestUtils.readInput(TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/rename/" + testFile);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, list.get(0));

    PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiElement resolved = ref == null ? null : ref.resolve();
    if (resolved instanceof PsiMethod && !(resolved instanceof GrAccessorMethod)) {
      PsiMethod method = (PsiMethod)resolved;
      String name = method.getName();
      String newName = createNewNameForMethod(name);
      myFixture.renameElementAtCaret(newName);
    } else if (resolved instanceof GrAccessorMethod) {
      GrField field = ((GrAccessorMethod)resolved).getProperty();
      RenameProcessor processor = new RenameProcessor(myFixture.getProject(), field, "newName", true, true);
      processor.addElement(resolved, createNewNameForMethod(((GrAccessorMethod)resolved).getName()));
      processor.run();
    } else {
      myFixture.renameElementAtCaret("newName");
    }
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResult(list.get(1));
  }

  private String createNewNameForMethod(final String name) {
    String newName = "newName";
    if (name.startsWith("get")) {
      newName = "get" + StringUtil.capitalize(newName);
    } else if (name.startsWith("is")) {
      newName = "is" + StringUtil.capitalize(newName);
    } else if (name.startsWith("set")) {
      newName = "set" + StringUtil.capitalize(newName);
    }
    return newName;
  }

}
