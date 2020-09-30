// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.MutablyNamedIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ComparisonUtils;

public class FlipComparisonIntention extends MutablyNamedIntention {
  @Override
  protected @IntentionName String getTextForElement(PsiElement element) {
    final GrBinaryExpression binaryExpression = (GrBinaryExpression) element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final String comparison = ComparisonUtils.getStringForComparison(tokenType);
    final String flippedComparison = ComparisonUtils.getFlippedComparison(tokenType);

    if (comparison.equals(flippedComparison)) {
      return GroovyIntentionsBundle.message("flip.smth.intention.name", comparison);
    }

    return GroovyIntentionsBundle.message("flip.comparison.intention.name", comparison, flippedComparison);
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ComparisonPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor)
      throws IncorrectOperationException {
    final GrBinaryExpression exp =
        (GrBinaryExpression) element;
    final IElementType tokenType = exp.getOperationTokenType();

    final GrExpression lhs = exp.getLeftOperand();
    final String lhsText = lhs.getText();

    final GrExpression rhs = exp.getRightOperand();
    final String rhsText = rhs.getText();

    final String flippedComparison = ComparisonUtils.getFlippedComparison(tokenType);

    final String newExpression =
        rhsText + flippedComparison + lhsText;
    PsiImplUtil.replaceExpression(newExpression, exp);
  }

}
