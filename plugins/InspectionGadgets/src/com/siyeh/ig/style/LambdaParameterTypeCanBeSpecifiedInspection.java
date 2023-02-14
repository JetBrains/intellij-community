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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LambdaParameterTypeCanBeSpecifiedInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.descriptor", infos);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InferLambdaParameterTypeVisitor();
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new InferLambdaParameterTypeFix((String)infos[0]);
  }


  private static class InferLambdaParameterTypeVisitor extends BaseInspectionVisitor {
    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression lambdaExpression) {
      super.visitLambdaExpression(lambdaExpression);
      PsiParameterList parameterList = lambdaExpression.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) return;
      for (PsiParameter parameter : parameters) {
        if (parameter.getTypeElement() != null) {
          return;
        }
      }
      final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null &&
          LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) != null) {
        final String inferredTypesText = LambdaUtil.createLambdaParameterListWithFormalTypes(functionalInterfaceType, lambdaExpression,
                                                                                             true);
        if (inferredTypesText != null) {
          PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(parameterList);
          if (PsiUtil.isJavaToken(nextElement, JavaTokenType.ARROW)) {
            registerErrorAtRange(parameterList, nextElement, inferredTypesText);
          }
          else {
            registerError(parameterList, inferredTypesText);
          }
        }
      }
    }
  }

  private static class InferLambdaParameterTypeFix extends InspectionGadgetsFix {
    private final String mySignatureText;

    InferLambdaParameterTypeFix(String signatureText) {
      mySignatureText = signatureText;
    }

    @Nls
    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.quickfix", mySignatureText);
    }

    @Nls
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.family.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiLambdaExpression) {
        LambdaUtil.specifyLambdaParameterTypes((PsiLambdaExpression)element);
      }
    }
  }
}
