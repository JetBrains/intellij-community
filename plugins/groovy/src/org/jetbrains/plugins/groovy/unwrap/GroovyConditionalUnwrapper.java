package org.jetbrains.plugins.groovy.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class GroovyConditionalUnwrapper extends GroovyUnwrapper {
  public GroovyConditionalUnwrapper() {
    super(CodeInsightBundle.message("unwrap.conditional"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e.getParent() instanceof GrConditionalExpression;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    GrConditionalExpression cond = (GrConditionalExpression)element.getParent();

    PsiElement savedBlock;

    if (cond.getElseBranch() == element) {
      savedBlock = element;
    }
    else {
      savedBlock = cond.getThenBranch();
    }

    context.extractElement(savedBlock, cond);

    context.deleteExactly(cond);
  }
}
