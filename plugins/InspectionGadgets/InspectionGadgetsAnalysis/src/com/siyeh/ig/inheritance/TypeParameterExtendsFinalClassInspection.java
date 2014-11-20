/*
 * Copyright 2006-2014 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeParameterExtendsFinalClassInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("type.parameter.extends.final.class.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Integer problemType = (Integer)infos[1];
    final PsiNamedElement namedElement = (PsiNamedElement)infos[0];
    final String name = namedElement.getName();
    if (problemType.intValue() == 1) {
      return InspectionGadgetsBundle.message("type.parameter.extends.final.class.problem.descriptor1", name);
    }
    else {
      return InspectionGadgetsBundle.message("type.parameter.extends.final.class.problem.descriptor2", name);
    }
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new TypeParameterExtendsFinalClassFix();
  }

  private static class TypeParameterExtendsFinalClassFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("type.parameter.extends.final.class.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = (PsiTypeParameter)parent;
        replaceTypeParameterUsagesWithType(typeParameter);
        typeParameter.delete();
      }
      else if (parent instanceof PsiTypeElement) {
        final PsiTypeElement typeElement = (PsiTypeElement)parent;
        final PsiElement lastChild = typeElement.getLastChild();
        if (lastChild == null) {
          return;
        }
        typeElement.replace(lastChild);
      }
    }

    private static void replaceTypeParameterUsagesWithType(PsiTypeParameter typeParameter) {
      final PsiClassType[] types = typeParameter.getExtendsList().getReferencedTypes();
      if (types.length < 1) {
        return;
      }
      final Project project = typeParameter.getProject();
      final PsiJavaCodeReferenceElement classReference = JavaPsiFacade.getElementFactory(project).createReferenceElementByType(types[0]);
      final Query<PsiReference> query = ReferencesSearch.search(typeParameter, typeParameter.getUseScope());
      for (PsiReference reference : query) {
        final PsiElement referenceElement = reference.getElement();
        referenceElement.replace(classReference);
      }
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TypeParameterExtendsFinalClassVisitor();
  }

  private static class TypeParameterExtendsFinalClassVisitor extends BaseInspectionVisitor {
    @Override
    public void visitTypeParameter(PsiTypeParameter classParameter) {
      super.visitTypeParameter(classParameter);
      final PsiClassType[] extendsListTypes = classParameter.getExtendsListTypes();
      if (extendsListTypes.length < 1) {
        return;
      }
      final PsiClassType extendsType = extendsListTypes[0];
      final PsiClass aClass = extendsType.resolve();
      if (aClass == null || !aClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiIdentifier nameIdentifier = classParameter.getNameIdentifier();
      if (nameIdentifier != null) {
        registerError(nameIdentifier, aClass, Integer.valueOf(1));
      }
    }

    @Override
    public void visitTypeElement(PsiTypeElement typeElement) {
      super.visitTypeElement(typeElement);
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiWildcardType)) {
        return;
      }
      final PsiWildcardType wildcardType = (PsiWildcardType)type;
      final PsiType extendsBound = wildcardType.getExtendsBound();
      if (!(extendsBound instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)extendsBound;
      final PsiClass aClass = classType.resolve();
      if (aClass == null || !aClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (aClass.hasTypeParameters() && !PsiUtil.isLanguageLevel8OrHigher(typeElement)) {
        final PsiType[] parameters = classType.getParameters();
        if (parameters.length == 0) {
          return;
        }
        for (PsiType parameter : parameters) {
          if (parameter instanceof PsiWildcardType) {
            return;
          }
        }
      }
      if (!shouldReport(typeElement)) {
        return;
      }
      registerError(typeElement.getFirstChild(), aClass, Integer.valueOf(2));
    }

    private static boolean shouldReport(PsiTypeElement typeElement) {
      final PsiElement ancestor = PsiTreeUtil.skipParentsOfType(
        typeElement, PsiTypeElement.class, PsiJavaCodeReferenceElement.class, PsiReferenceParameterList.class);
      if (ancestor instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)ancestor;
        final PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)scope;
          if (MethodUtils.hasSuper(method)) {
            return false;
          }
        }
        else if (scope instanceof PsiForeachStatement) {
          final PsiForeachStatement foreachStatement = (PsiForeachStatement)scope;
          final PsiParameter iterationParameter = foreachStatement.getIterationParameter();
          final PsiType iterationType = iterationParameter.getType();
          final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
          if (iteratedValue == null) {
            return false; // incomplete code
          }
          final PsiType type = JavaGenericsUtil.getCollectionItemType(iteratedValue);
          if (type == null || !TypeConversionUtil.isAssignable(iterationType, type)) { // sanity check
            return false;
          }
          if (type.equals(iterationType)) {
            return false;
          }
          if (!(type instanceof PsiCapturedWildcardType)) {
            return true;
          }
          final PsiCapturedWildcardType capturedWildcardType = (PsiCapturedWildcardType)type;
          final PsiType upperBound = capturedWildcardType.getUpperBound();
          if (iterationType.equals(upperBound)) {
            return false;
          }
        }
      }
      else if (ancestor instanceof PsiLocalVariable) {
        final PsiLocalVariable localVariable = (PsiLocalVariable)ancestor;
        final PsiExpression initializer = localVariable.getInitializer();
        if (initializer == null) {
          return true;
        }
        final PsiType type = initializer.getType();
        final PsiType expectedType = GenericsUtil.getVariableTypeByExpressionType(type);
        final PsiType variableType = localVariable.getType();
        if (variableType.equals(expectedType)) {
          return false;
        }
      }
      return true;
    }
  }
}