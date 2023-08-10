// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author Max Medvedev
 */
public class GrSyntheticLiteralExpression extends LightElement implements PsiLiteralExpression {
  private final GrLiteral myLiteral;

  public GrSyntheticLiteralExpression(GrLiteral expression) {
    super(expression.getManager(), expression.getLanguage());
    myLiteral = expression;
  }

  @Override
  public String toString() {
    return myLiteral.toString();
  }

  @Override
  public PsiType getType() {
    return myLiteral.getType();
  }

  @Override
  public TextRange getTextRange() {
    return myLiteral.getTextRange();
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return myLiteral.replace(newElement);
  }

  @Override
  public int getStartOffsetInParent() {
    return myLiteral.getStartOffsetInParent();
  }

  @Override
  public PsiFile getContainingFile() {
    return myLiteral.getContainingFile();
  }

  @Override
  public int getTextOffset() {
    return myLiteral.getTextOffset();
  }

  @Override
  public String getText() {
    return myLiteral.getText();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myLiteral.getNavigationElement();
  }

  @Override
  public boolean isValid() {
    return myLiteral.isValid();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLiteralExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }


  @Override
  public @Nullable Object getValue() {
    return myLiteral.getValue();
  }
}
