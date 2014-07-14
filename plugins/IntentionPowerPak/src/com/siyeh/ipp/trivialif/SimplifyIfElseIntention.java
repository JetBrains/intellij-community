/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaToken;
import com.siyeh.ig.controlflow.TrivialIfInspection;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class SimplifyIfElseIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiJavaToken)) {
          return false;
        }
        final PsiJavaToken token = (PsiJavaToken)element;
        final PsiElement parent = token.getParent();
        if (!(parent instanceof PsiIfStatement)) {
          return false;
        }
        return TrivialIfInspection.isTrivial((PsiIfStatement)parent);
      }
    };
  }

  public void processIntention(@NotNull PsiElement element) {
    TrivialIfInspection.simplify((PsiIfStatement)element.getParent());
  }
}