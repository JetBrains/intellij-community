package org.jetbrains.plugins.groovy.intentions
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.intentions.declaration.GrIntroduceLocalVariableIntention
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyIntroduceVariableDialog
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyIntroduceVariableSettings
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GroovyVariableValidator
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author siosio
 */
public class IntroduceLocalVariableTest extends GrIntentionTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/introduceLocalVariable/";
  }

  public void testMethodCall1() {doTest()}
  public void testMethodCall2() {doTest()}
  public void testMethodCall3() {doTest()}
  public void testMethodCall4() {doTest()}
  public void testConstructor() {doTest()}
  public void testClosure1() {doTest()}
  public void testClosure2() {doTest()}

  protected void doTest() {
    myFixture.configureByFile("${getTestName(false)}.groovy")
    def intentions = myFixture.availableIntentions

    for (intention in intentions) {
      if (intention instanceof IntentionActionWrapper) intention = intention.delegate
      if (intention instanceof GrIntroduceLocalVariableIntention) {
        new MockGrIntroduceLocalVariableIntention().invoke(myFixture.project, myFixture.editor, myFixture.file)
      }
    }
    myFixture.checkResultByFile("${getTestName(false)}-after.groovy")
  }

  static class MockGrIntroduceLocalVariableIntention extends GrIntroduceLocalVariableIntention {
    @Override
    protected void processIntention(PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
      setSelection(editor, getTargetExpression(element));
      new MockGrIntroduceVariableHandler().invoke(project, editor, element.containingFile, null);
    }
  }

  static class MockGrIntroduceVariableHandler extends GrIntroduceVariableHandler {
    @Override
    protected GroovyIntroduceVariableDialog getDialog(GrIntroduceContext context) {
      new MockGrIntroduceVariableDialog(context, new GroovyVariableValidator(context))
    }
  }

  static class MockGrIntroduceVariableDialog extends GroovyIntroduceVariableDialog {
    MockGrIntroduceVariableDialog(GrIntroduceContext context, GroovyVariableValidator validator) {
      super(context, validator)
    }

    @Override
    void show() {
      close(0)
    }

    @Override
    MockSettings getSettings() {
      new MockSettings()
    }

    @Override
    boolean isOK() {
      true
    }
  }

  static class MockSettings implements GroovyIntroduceVariableSettings {

    @Override
    boolean isDeclareFinal() {
      return false
    }

    @Override
    String getName() {
      return "varName"
    }

    @Override
    boolean replaceAllOccurrences() {
      return false
    }

    @Override
    PsiType getSelectedType() {
      return null
    }
  }
}
