/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidGotoDeclarationHandler implements GotoDeclarationHandler {
  @Override
  public PsiElement[] getGotoDeclarationTargets(PsiElement sourceElement) {
    if (!(sourceElement instanceof PsiIdentifier)) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(sourceElement);
    if (facet == null) {
      return null;
    }

    final PsiReferenceExpression refExp = PsiTreeUtil.getParentOfType(sourceElement, PsiReferenceExpression.class);
    if (refExp == null) {
      return null;
    }

    final PsiElement resolvedElement = refExp.resolve();
    if (resolvedElement == null || !(resolvedElement instanceof PsiField)) {
      return null;
    }

    final PsiField resolvedField = (PsiField)resolvedElement;
    final PsiFile containingFile = resolvedField.getContainingFile();

    if (containingFile == null || !AndroidResourceUtil.isRJavaField(containingFile, resolvedField)) {
      return null;
    }

    final PsiElement[] resources = AndroidResourceUtil.findResources(resolvedField);
    return resources.length > 0 ? resources : null;
  }
}
