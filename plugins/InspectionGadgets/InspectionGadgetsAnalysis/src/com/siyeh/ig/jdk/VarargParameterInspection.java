/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
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
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("variable.argument.method.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("variable.argument.method.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new VarargParameterFix();
  }

  private static class VarargParameterFix extends InspectionGadgetsFix {

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("variable.argument.method.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
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
      final Collection<PsiReference> references = ReferencesSearch.search(method).findAll();
      final List<PsiElement> prepare = new ArrayList<>();
      prepare.add(parameterList);
      for (PsiReference reference : references) {
        prepare.add(reference.getElement());
      }
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(prepare)) {
        return;
      }
      WriteAction.run(() -> {
        final PsiEllipsisType type = (PsiEllipsisType)lastParameter.getType();
        final PsiType componentType = type.getComponentType();
        final String typeText;
        if (componentType instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)componentType;
          typeText = classType.rawType().getCanonicalText();
        } else {
          typeText = componentType.getCanonicalText();
        }
        for (PsiReference reference : references) {
          modifyCall(reference, typeText, parameters.length - 1);
        }
        final PsiType arrayType = type.toArrayType();
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(lastParameter.getProject());
        final PsiTypeElement newTypeElement = factory.createTypeElement(arrayType);
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, "java.lang.SafeVarargs");
        if (annotation != null) {
          annotation.delete();
        }
        typeElement.replace(newTypeElement);
      });
    }

    public static void modifyCall(PsiReference reference, String arrayTypeText, int indexOfFirstVarargArgument) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)reference.getElement();
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)referenceExpression.getParent();
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      @NonNls final StringBuilder builder = new StringBuilder("new ");
      builder.append(arrayTypeText);
      builder.append("[]{");
      if (arguments.length > indexOfFirstVarargArgument) {
        final PsiExpression firstArgument = arguments[indexOfFirstVarargArgument];
        final String firstArgumentText = firstArgument.getText();
        builder.append(firstArgumentText);
        for (int i = indexOfFirstVarargArgument + 1; i < arguments.length; i++) {
          builder.append(',').append(arguments[i].getText());
        }
      }
      builder.append('}');
      final Project project = referenceExpression.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression arrayExpression = factory.createExpressionFromText(builder.toString(), referenceExpression);
      if (arguments.length > indexOfFirstVarargArgument) {
        final PsiExpression firstArgument = arguments[indexOfFirstVarargArgument];
        argumentList.deleteChildRange(firstArgument, arguments[arguments.length - 1]);
        argumentList.add(arrayExpression);
      }
      else {
        argumentList.add(arrayExpression);
      }
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