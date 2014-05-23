/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myExpression.getNavigationElement();
  }

  @Override
  public boolean isValid() {
    return myExpression.isValid();
  }
}
