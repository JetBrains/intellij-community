/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class CanBeStaticVisitor extends JavaRecursiveElementWalkingVisitor {
  private boolean canBeStatic = true;

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (canBeStatic) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
    if (!canBeStatic) {
      return;
    }
    super.visitReferenceExpression(ref);
    PsiElement element = ref.resolve();

    if (element instanceof PsiModifierListOwner && !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
      canBeStatic = false;
    }
  }

  public boolean canBeStatic() {
    return canBeStatic;
  }
}
