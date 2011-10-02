/*
 * Copyright 2006-2011 Bas Leijdekkers
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
package com.siyeh.ipp.varargs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

class ConvertVarargParameterToArrayPredicate
  implements PsiElementPredicate {

  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof PsiParameterList)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiMethod)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)parent;
    return method.isVarArgs();
  }
}
