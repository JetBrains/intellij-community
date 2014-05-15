/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public class GroovyPsiUtil {
  public static boolean checkPsiElementsAreEqual(PsiElement l, PsiElement r) {
    if (!l.getText().equals(r.getText())) return false;
    if (l.getNode().getElementType() != r.getNode().getElementType()) return false;

    final PsiElement[] lChildren = l.getChildren();
    final PsiElement[] rChildren = r.getChildren();

    if (lChildren.length != rChildren.length) return false;

    for (int i = 0; i < rChildren.length; i++) {
      if (!checkPsiElementsAreEqual(lChildren[i], rChildren[i])) return false;
    }
    return true;
  }

  public static boolean isCall(GrReferenceExpression referenceExpression) {
    return referenceExpression.getParent() instanceof GrCall;
  }
}
