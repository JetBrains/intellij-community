/*
 * Copyright 2006-2014 Bas Leijdekkers
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
package com.siyeh.ipp.parenthesis;

import com.intellij.psi.PsiElement;
import com.siyeh.ig.style.UnclearBinaryExpressionInspection;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class AddClarifyingParenthesesIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new AddClarifyingParenthesesPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    UnclearBinaryExpressionInspection.replaceElement(element);
  }
}