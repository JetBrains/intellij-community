/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtilRt;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Bas Leijdekkers
 */
public class ParameterTypePreventsOverridingInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("parameter.type.prevents.overriding.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final String qualifiedName1 = (String)infos[0];
    final String packageName = StringUtil.getPackageName(qualifiedName1);
    final String qualifiedName2 = (String)infos[1];
    final String superPackageName = StringUtil.getPackageName(qualifiedName2);
    return InspectionGadgetsBundle.message("parameter.type.prevents.overriding.problem.descriptor", packageName, superPackageName);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ParameterTypePreventsOverridingFix((String)infos[1]);
  }

  private static class ParameterTypePreventsOverridingFix extends InspectionGadgetsFix {

    private final String myNewTypeText;

    public ParameterTypePreventsOverridingFix(String newTypeText) {
      myNewTypeText = newTypeText;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("parameter.type.prevents.overriding.quickfix", myNewTypeText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("parameter.type.prevents.overriding.family.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeElement)) {
        return;
      }
      final PsiTypeElement typeElement = (PsiTypeElement)element;
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(typeElement.getProject());
      final PsiTypeElement newTypeElement = factory.createTypeElementFromText(myNewTypeText, typeElement);
      typeElement.replace(newTypeElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ParameterTypePreventsOverridingVisitor();
  }

  private static class ParameterTypePreventsOverridingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final int parameterCount = parameterList.getParametersCount();
      if (parameterCount == 0) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiClass superClass = containingClass.getSuperClass();
      if (superClass == null) {
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final String name = method.getName();
      final PsiMethod[] superMethods = superClass.findMethodsByName(name, true);
      outer: for (PsiMethod superMethod : superMethods) {
        final PsiType superReturnType = superMethod.getReturnType();
        if (superReturnType == null || !superReturnType.isAssignableFrom(returnType)) {
          continue;
        }
        final PsiParameterList superParameterList = superMethod.getParameterList();
        if (superParameterList.getParametersCount() != parameterCount) {
          continue;
        }
        final PsiParameter[] superParameters = superParameterList.getParameters();
        final Map<PsiTypeElement, PsiClassType> problemTypeElements = ContainerUtilRt.newHashMap(2);
        for (int i = 0; i < parameters.length; i++) {
          final PsiParameter parameter = parameters[i];
          final PsiParameter superParameter = superParameters[i];
          final PsiType type = parameter.getType();
          final PsiType superType = superParameter.getType();
          if (!(superType instanceof PsiClassType) || type.equals(superType)) {
            continue;
          }
          if (!(type instanceof PsiClassType) || !type.getPresentableText().equals(superType.getPresentableText())) {
            return;
          }
          final PsiTypeElement typeElement = parameter.getTypeElement();
          if (typeElement == null) {
            return;
          }
          final PsiTypeElement superParameterTypeElement = superParameter.getTypeElement();
          if (superParameterTypeElement == null) {
            continue outer;
          }
          problemTypeElements.put(typeElement, (PsiClassType)superType);
        }
        for (Map.Entry<PsiTypeElement, PsiClassType> entry : problemTypeElements.entrySet()) {
          final PsiTypeElement typeElement = entry.getKey();
          final PsiClassType type = (PsiClassType)typeElement.getType();
          final PsiClass aClass1 = type.resolve();
          if (aClass1 == null || aClass1 instanceof PsiTypeParameter) {
            return;
          }
          final PsiClassType classType = entry.getValue();
          final PsiClass aClass2 = classType.resolve();
          if (aClass2 == null || aClass2 instanceof PsiTypeParameter) {
            continue;
          }
          registerError(typeElement, type.getCanonicalText(), classType.getCanonicalText());
        }
      }
    }
  }
}
