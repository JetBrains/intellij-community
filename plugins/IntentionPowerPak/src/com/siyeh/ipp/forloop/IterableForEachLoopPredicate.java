/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class IterableForEachLoopPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    final IElementType tokenType = token.getTokenType();
    if (!JavaTokenType.FOR_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiForeachStatement)) {
      return false;
    }
    final PsiForeachStatement foreachStatement = (PsiForeachStatement)parent;
    final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
    if (iteratedValue == null) {
      return false;
    }
    final PsiType type = iteratedValue.getType();
    if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)) {
      return false;
    }
    return !ErrorUtil.containsError(foreachStatement);
  }
}