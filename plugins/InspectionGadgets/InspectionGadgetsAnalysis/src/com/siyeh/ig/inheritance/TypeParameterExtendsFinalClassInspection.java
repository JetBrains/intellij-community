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
    return problemType.intValue() == 1
           ? InspectionGadgetsBundle.message("type.parameter.extends.final.class.problem.descriptor1", name)
           : InspectionGadgetsBundle.message("type.parameter.extends.final.class.problem.descriptor2", name);
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
      if (isWildcardRequired(typeElement)) {
        return;
      }
      registerError(typeElement.getFirstChild(), aClass, Integer.valueOf(2));
    }

    private static boolean isWildcardRequired(PsiTypeElement typeElement) {
      final PsiElement ancestor = PsiTreeUtil.skipParentsOfType(
        typeElement, PsiTypeElement.class, PsiJavaCodeReferenceElement.class, PsiReferenceParameterList.class);
      if (ancestor instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)ancestor;
        final PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)scope;
          if (MethodUtils.hasSuper(method)) {
            return true;
          }
        }
        else if (scope instanceof PsiForeachStatement) {
          final PsiForeachStatement foreachStatement = (PsiForeachStatement)scope;
          final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
          if (iteratedValue == null) {
            return true; // incomplete code
          }
          final PsiParameter iterationParameter = foreachStatement.getIterationParameter();
          return isWildcardRequired(typeElement, iterationParameter.getTypeElement(), JavaGenericsUtil.getCollectionItemType(iteratedValue));
        }
      }
      else if (ancestor instanceof PsiLocalVariable) {
        final PsiLocalVariable localVariable = (PsiLocalVariable)ancestor;
        final PsiExpression initializer = localVariable.getInitializer();
        return initializer != null && isWildcardRequired(typeElement, localVariable.getTypeElement(), initializer.getType());
      }
      return false;
    }

    private static boolean isWildcardRequired(PsiTypeElement innerTypeElement, PsiTypeElement completeTypeElement, PsiType rhsType) {
      final PsiType lhsType = completeTypeElement.getType();
      if (lhsType.equals(rhsType) || rhsType == null || !TypeConversionUtil.isAssignable(lhsType, rhsType)) {
        return true;
      }
      final Object marker = new Object();
      PsiTreeUtil.mark(innerTypeElement, marker);
      final PsiTypeElement copy = (PsiTypeElement)completeTypeElement.copy();
      final PsiElement markedElement = PsiTreeUtil.releaseMark(copy, marker);
      assert markedElement != null;
      markedElement.replace(markedElement.getLastChild());
      return !TypeConversionUtil.isAssignable(copy.getType(), rhsType);
    }
  }
}