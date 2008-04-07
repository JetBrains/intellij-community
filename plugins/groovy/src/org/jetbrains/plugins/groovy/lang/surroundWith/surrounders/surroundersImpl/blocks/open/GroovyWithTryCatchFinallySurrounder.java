package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyWithTryCatchFinallySurrounder extends GroovyWithTryCatchSurrounder {

  public String getTemplateDescription() {
    return super.getTemplateDescription() + " / finally";
  }

  protected GroovyPsiElement doSurroundElements(PsiElement[] elements) throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(elements[0].getProject());
    GrTryCatchStatement tryStatement = (GrTryCatchStatement) factory.createTopElementFromText("try {} catch(exception e){\n} finally{\n}");
    addStatements(tryStatement.getTryBlock(), elements);
    return tryStatement;
  }
}
