/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class VarargParameterInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "VariableArgumentMethod";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("variable.argument.method.problem.descriptor");
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new VarargParameterFix();
  }

  private static class VarargParameterFix extends PsiUpdateModCommandQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("variable.argument.method.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiMethod method = (PsiMethod)element.getParent();
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) {
        return;
      }
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      if (!lastParameter.isVarArgs()) {
        return;
      }
      final PsiTypeElement typeElement = lastParameter.getTypeElement();
      if (typeElement == null) {
        return;
      }
      final List<PsiElement> refElements = ContainerUtil.map(getReferences(method), e -> updater.getWritable(e));
      performModification(method, parameters, lastParameter, typeElement, refElements);
    }

    @NotNull
    private static List<PsiElement> getReferences(@NotNull PsiMethod method) {
      if (IntentionPreviewUtils.isIntentionPreviewActive()) {
        return SyntaxTraverser.psiTraverser(method.getContainingFile())
          .filter(ref -> ref instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)ref).isReferenceTo(method) ||
                         ref instanceof PsiEnumConstant && method.isEquivalentTo(((PsiEnumConstant)ref).resolveMethod()))
          .toList();
      }
      final Collection<PsiReference> references = ReferencesSearch.search(method).findAll();
      final List<PsiElement> refElements = new ArrayList<>();
      for (PsiReference reference : references) {
        refElements.add(reference.getElement());
      }
      return refElements;
    }

    private static void performModification(@NotNull PsiMethod method,
                                            @NotNull PsiParameter @NotNull [] parameters,
                                            @NotNull PsiParameter lastParameter,
                                            @NotNull PsiTypeElement typeElement,
                                            @NotNull List<PsiElement> references) {
      final PsiEllipsisType type = (PsiEllipsisType)lastParameter.getType();
      final PsiType componentType = type.getComponentType();
      final String typeText;
      if (componentType instanceof PsiClassType classType) {
        typeText = classType.rawType().getCanonicalText();
      }
      else {
        typeText = componentType.getCanonicalText();
      }
      for (PsiElement reference : references) {
        modifyCall(typeText, parameters.length - 1, reference);
      }
      final PsiType arrayType = type.toArrayType();
      final PsiTypeElement newTypeElement = JavaPsiFacade.getElementFactory(lastParameter.getProject()).createTypeElement(arrayType);
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, CommonClassNames.JAVA_LANG_SAFE_VARARGS);
      if (annotation != null) {
        annotation.delete();
      }
      typeElement.replace(newTypeElement);
    }

    public static void modifyCall(String arrayTypeText, int indexOfFirstVarargArgument, @NotNull PsiElement reference) {
      final PsiCall call = (PsiCall)(reference instanceof PsiCall ? reference : reference.getParent());
      JavaResolveResult result = call.resolveMethodGenerics();
      if (result instanceof MethodCandidateInfo &&
          ((MethodCandidateInfo)result).getApplicabilityLevel() != MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      @NonNls final StringBuilder builder = new StringBuilder("new ").append(arrayTypeText).append("[]{");
      if (arguments.length > indexOfFirstVarargArgument) {
        final PsiExpression firstArgument = arguments[indexOfFirstVarargArgument];
        builder.append(firstArgument.getText());
        for (int i = indexOfFirstVarargArgument + 1; i < arguments.length; i++) {
          builder.append(',').append(arguments[i].getText());
        }
      }
      builder.append('}');
      final Project project = reference.getProject();
      final PsiExpression arrayExpression =
        JavaPsiFacade.getElementFactory(project).createExpressionFromText(builder.toString(), reference);
      if (arguments.length > indexOfFirstVarargArgument) {
        final PsiExpression firstArgument = arguments[indexOfFirstVarargArgument];
        argumentList.deleteChildRange(firstArgument, arguments[arguments.length - 1]);
      }
      argumentList.add(arrayExpression);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(argumentList);
      CodeStyleManager.getInstance(project).reformat(argumentList);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new VarargParameterVisitor();
  }

  private static class VarargParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 0) {
        return;
      }
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      if (lastParameter.isVarArgs()) {
        registerMethodError(method);
      }
    }
  }
}