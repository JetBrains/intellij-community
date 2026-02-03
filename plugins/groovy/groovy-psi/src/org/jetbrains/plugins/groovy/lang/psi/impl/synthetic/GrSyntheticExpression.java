// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Max Medvedev
 */
public class GrSyntheticExpression extends LightElement implements PsiExpression {
  private final GrExpression myExpression;

  public GrSyntheticExpression(GrExpression expression) {
    super(expression.getManager(), expression.getLanguage());
    myExpression = expression;
  }

  @Override
  public String toString() {
    return myExpression.toString();
  }

  @Override
  public PsiType getType() {
    return myExpression.getType();
  }

  @Override
  public TextRange getTextRange() {
    return myExpression.getTextRange();
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return myExpression.replace(newElement);
  }

  @Override
  public int getStartOffsetInParent() {
    return myExpression.getStartOffsetInParent();
  }

  @Override
  public PsiFile getContainingFile() {
    return myExpression.getContainingFile();
  }

  @Override
  public int getTextOffset() {
    return myExpression.getTextOffset();
  }

  @Override
  public String getText() {
    return myExpression.getText();
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return myExpression.getNavigationElement();
  }

  @Override
  public boolean isValid() {
    return myExpression.isValid();
  }
}
