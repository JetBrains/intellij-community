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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class FlipConjunctionIntention extends MutablyNamedIntention {
  @Override
  protected @IntentionName String getTextForElement(PsiElement element) {
    final GrBinaryExpression binaryExpression = (GrBinaryExpression)element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final String conjunction = getConjunction(tokenType);
    return GroovyIntentionsBundle.message("flip.smth.intention.name", conjunction);
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConjunctionPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final GrBinaryExpression exp = (GrBinaryExpression)element;
    final IElementType tokenType = exp.getOperationTokenType();

    final GrExpression lhs = exp.getLeftOperand();
    final String lhsText = lhs.getText();

    final GrExpression rhs = exp.getRightOperand();
    final String rhsText = rhs.getText();

    final String conjunction = getConjunction(tokenType);

    final String newExpression = rhsText + conjunction + lhsText;
    PsiImplUtil.replaceExpression(newExpression, exp);
  }

  private static String getConjunction(IElementType tokenType) {
    return tokenType.equals(GroovyTokenTypes.mLAND) ? "&&" : "||";
  }
}
