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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LambdaParameterTypeCanBeSpecifiedInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance("#" + LambdaParameterTypeCanBeSpecifiedInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.descriptor", infos);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InferLambdaParameterTypeVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new InferLambdaParameterTypeFix(infos);
  }

  private static void doFix(@NotNull Project project, @NotNull PsiLambdaExpression lambdaExpression) {
    final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
    final String buf = getInferredTypes(functionalInterfaceType, lambdaExpression, true);
    final PsiMethod methodFromText = JavaPsiFacade.getElementFactory(project).createMethodFromText("void foo" + buf, lambdaExpression);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(lambdaExpression.getParameterList().replace(methodFromText.getParameterList()));
  }

  @Nullable
  private static String getInferredTypes(PsiType functionalInterfaceType, final PsiLambdaExpression lambdaExpression, boolean useFQN) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final StringBuilder buf = new StringBuilder();
    buf.append("(");
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    LOG.assertTrue(interfaceMethod != null);
    final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
    final PsiParameter[] lambdaParameters = lambdaExpression.getParameterList().getParameters();
    if (parameters.length != lambdaParameters.length) return null;
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType psiType = LambdaUtil.getSubstitutor(interfaceMethod, resolveResult).substitute(parameter.getType());
      if (!PsiTypesUtil.isDenotableType(psiType)) return null;
      if (psiType != null) {
        buf.append(useFQN ? psiType.getCanonicalText() : psiType.getPresentableText()).append(" ").append(lambdaParameters[i].getName());
      }
      else {
        buf.append(lambdaParameters[i].getName());
      }
      if (i < parameters.length - 1) {
        buf.append(", ");
      }
    }
    buf.append(")");
    return buf.toString();
  }

  private static class InferLambdaParameterTypeVisitor extends BaseInspectionVisitor {
    @Override
    public void visitLambdaExpression(PsiLambdaExpression lambdaExpression) {
      super.visitLambdaExpression(lambdaExpression);
      final PsiParameter[] parameters = lambdaExpression.getParameterList().getParameters();
      if (parameters.length == 0) return;
      for (PsiParameter parameter : parameters) {
        if (parameter.getTypeElement() != null) {
          return;
        }
      }
      final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null &&
          LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) != null &&
          LambdaUtil.isLambdaFullyInferred(lambdaExpression, functionalInterfaceType)) {
        final String inferredTypesText = getInferredTypes(functionalInterfaceType, lambdaExpression, false);
        if (inferredTypesText != null) {
          registerError(lambdaExpression, inferredTypesText);
        }
      }
    }
  }

  private static class InferLambdaParameterTypeFix extends InspectionGadgetsFix {
    private final Object[] myInfos;

    public InferLambdaParameterTypeFix(Object... infos) {
      myInfos = infos;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.quickfix", myInfos);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.family.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiLambdaExpression) {
        LambdaParameterTypeCanBeSpecifiedInspection.doFix(project, (PsiLambdaExpression)element);
      }
    }
  }
}
