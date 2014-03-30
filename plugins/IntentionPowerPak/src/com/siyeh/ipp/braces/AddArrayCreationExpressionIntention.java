/*
 * Copyright 2011-2013 Bas Leijdekkers
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
package com.siyeh.ipp.braces;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class AddArrayCreationExpressionIntention extends MutablyNamedIntention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ArrayCreationExpressionPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)element;
    final PsiType type = arrayInitializerExpression.getType();
    return IntentionPowerPackBundle.message("add.array.creation.expression.intention.name",
                                            TypeConversionUtil.erasure(type).getCanonicalText());
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)element;
    final PsiType type = arrayInitializerExpression.getType();
    if (type == null) {
      return;
    }
    PsiReplacementUtil.replaceExpression(arrayInitializerExpression, "new " +
                                                                     TypeConversionUtil.erasure(type).getCanonicalText() +
                                                                     arrayInitializerExpression.getText());
  }
}
