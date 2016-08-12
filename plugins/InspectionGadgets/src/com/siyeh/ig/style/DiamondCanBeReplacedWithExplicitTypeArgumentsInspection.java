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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DiamondCanBeReplacedWithExplicitTypeArgumentsInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("diamond.can.be.replaced.with.explicit.type.arguments.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DiamondTypeVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DiamondTypeFix();
  }

  private static class DiamondTypeVisitor extends BaseInspectionVisitor {
    @Override
    public void visitReferenceParameterList(PsiReferenceParameterList referenceParameterList) {
      super.visitReferenceParameterList(referenceParameterList);
      final PsiTypeElement[] typeParameterElements = referenceParameterList.getTypeParameterElements();
      if (typeParameterElements.length == 1) {
        final PsiTypeElement typeParameterElement = typeParameterElements[0];
        final PsiType type = typeParameterElement.getType();
        if (type instanceof PsiDiamondType) {
          final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(referenceParameterList, PsiNewExpression.class);
          if (newExpression != null) {
            final List<PsiType> types = PsiDiamondTypeImpl.resolveInferredTypesNoCheck(newExpression, newExpression).getInferredTypes();
            if (!types.isEmpty()) {
              registerError(referenceParameterList);
            }
          }
        }
      }
    }
  }

  private static class DiamondTypeFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("diamond.can.be.replaced.with.explicit.type.arguments.quickfix");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiDiamondTypeUtil.replaceDiamondWithExplicitTypes(descriptor.getPsiElement());
    }
  }
}
