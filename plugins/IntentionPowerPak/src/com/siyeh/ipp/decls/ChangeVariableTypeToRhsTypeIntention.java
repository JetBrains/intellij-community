/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ipp.decls;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ChangeVariableTypeToRhsTypeIntention extends MutablyNamedIntention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ChangeVariableTypeToRhsTypePredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiVariable variable = (PsiVariable)element.getParent();
    final PsiExpression initializer = variable.getInitializer();
    assert initializer != null;
    final PsiType type = initializer.getType();
    assert type != null;
    return IntentionPowerPackBundle.message("change.variable.type.to.rhs.type.intention.name",
                                            variable.getName(), type.getPresentableText());
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiVariable)) {
      return;
    }
    final PsiVariable variable = (PsiVariable)parent;
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return;
    }
    final PsiType type = initializer.getType();
    if (type == null) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiTypeElement typeElement = factory.createTypeElement(type);
    final PsiTypeElement variableTypeElement = variable.getTypeElement();
    if (variableTypeElement == null) {
      return;
    }
    variableTypeElement.replace(typeElement);
  }
}
