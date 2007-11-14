package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyWithTryFinallySurrounder extends GroovyWithTrySurrounder {
  protected GroovyPsiElement doSurroundElements(PsiElement[] elements) throws IncorrectOperationException {
    GroovyElementFactory factory = GroovyElementFactory.getInstance(elements[0].getProject());
    GrTryCatchStatement tryStatement = (GrTryCatchStatement) factory.createTopElementFromText("try {\n} finally{\n}");
    addStatements(tryStatement.getTryBlock(), elements);
    return tryStatement;
  }

  public String getTemplateDescription() {
    return super.getTemplateDescription() + " / finally";
  }
}
