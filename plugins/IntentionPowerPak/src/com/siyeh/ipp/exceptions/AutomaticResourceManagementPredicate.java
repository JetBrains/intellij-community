/*
 * Copyright 2011 Bas Leijdekkers
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
import com.siyeh.ipp.base.PsiElementPredicate;

class AutomaticResourceManagementPredicate
  implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken javaToken = (PsiJavaToken)element;
    final IElementType tokenType = javaToken.getTokenType();
    if (!JavaTokenType.TRY_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiTryStatement)) {
      return false;
    }
    final PsiTryStatement tryStatement = (PsiTryStatement)parent;
    if (tryStatement.getTryBlock() == null) {
      return false;
    }
    final PsiResourceList resourceList = tryStatement.getResourceList();
    return resourceList != null;
  }
}
