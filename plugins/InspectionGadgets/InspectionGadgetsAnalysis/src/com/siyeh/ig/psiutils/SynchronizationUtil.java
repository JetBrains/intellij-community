/*
 * Copyright 2006-2010 Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public class SynchronizationUtil {

  private SynchronizationUtil() {
  }

  public static boolean isInSynchronizedContext(PsiElement element) {
    final PsiElement context =
      PsiTreeUtil.getParentOfType(element, PsiMethod.class,
                                  PsiSynchronizedStatement.class);
    if (context instanceof PsiSynchronizedStatement) {
      return true;
    }
    if (context == null) {
      return false;
    }
    final PsiModifierListOwner modifierListOwner =
      (PsiModifierListOwner)context;
    return modifierListOwner.hasModifierProperty(PsiModifier.SYNCHRONIZED);
  }
}