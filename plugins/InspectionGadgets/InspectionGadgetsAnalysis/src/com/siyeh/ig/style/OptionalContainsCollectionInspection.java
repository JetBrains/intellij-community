/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class OptionalContainsCollectionInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("optional.contains.collection.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message(
      type instanceof PsiArrayType ? "optional.contains.array.problem.descriptor" : "optional.contains.collection.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OptionalContainsCollectionVisitor();
  }

  private static class OptionalContainsCollectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeElement(PsiTypeElement typeElement) {
      super.visitTypeElement(typeElement);
      final PsiType type = typeElement.getType();
      if (!TypeUtils.isOptional(type)) {
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
      if (referenceElement == null) {
        return;
      }
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList == null) {
        return;
      }
      final PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
      if (typeParameterElements.length != 1) {
        return;
      }
      final PsiTypeElement typeParameterElement = typeParameterElements[0];
      final PsiType parameterType = typeParameterElement.getType();
      if (!(parameterType instanceof PsiArrayType) && !CollectionUtils.isCollectionClassOrInterface(parameterType)) {
        return;
      }
      registerError(typeParameterElement, parameterType);
    }
  }
}
