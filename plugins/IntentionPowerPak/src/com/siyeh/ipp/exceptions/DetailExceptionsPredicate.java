/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.Set;

class DetailExceptionsPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final IElementType tokenType = ((PsiJavaToken)element).getTokenType();
    if (!JavaTokenType.TRY_KEYWORD.equals(tokenType) && !JavaTokenType.CATCH_KEYWORD.equals(tokenType)) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (parent instanceof PsiCatchSection) {
      parent = parent.getParent();
    }
    if (!(parent instanceof PsiTryStatement)) {
      return false;
    }
    final PsiTryStatement tryStatement = (PsiTryStatement)parent;
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    final Set<PsiClassType> exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(tryBlock);
    ExceptionUtils.calculateExceptionsThrown(tryStatement.getResourceList(), exceptionsThrown);
    final Set<PsiType> exceptionsCaught = ExceptionUtils.getExceptionTypesHandled(tryStatement);
    for (PsiType typeThrown : exceptionsThrown) {
      if (exceptionsCaught.contains(typeThrown)) {
        continue;
      }
      for (PsiType typeCaught : exceptionsCaught) {
        if (typeCaught.isAssignableFrom(typeThrown)) {
          return true;
        }
      }
    }
    return false;
  }
}
