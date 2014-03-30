/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RawUseOfParameterizedTypeInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreObjectConstruction = true;

  @SuppressWarnings("PublicField") public boolean ignoreTypeCasts = false;

  @SuppressWarnings("PublicField") public boolean ignoreUncompilable = false;

  @SuppressWarnings("PublicField") public boolean ignoreParametersOfOverridingMethods = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("raw.use.of.parameterized.type.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("raw.use.of.parameterized.type.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("raw.use.of.parameterized.type.ignore.new.objects.option"),
                             "ignoreObjectConstruction");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("raw.use.of.parameterized.type.ignore.type.casts.option"),
                             "ignoreTypeCasts");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("raw.use.of.parameterized.type.ignore.uncompilable.option"),
                             "ignoreUncompilable");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("raw.use.of.parameterized.type.ignore.overridden.parameter.option"),
                             "ignoreParametersOfOverridingMethods");
    return optionsPanel;
  }

  @Override
  public String getAlternativeID() {
    return "rawtypes";
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RawUseOfParameterizedTypeVisitor();
  }

  private class RawUseOfParameterizedTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      if (!hasNeededLanguageLevel(expression)) {
        return;
      }
      super.visitNewExpression(expression);
      if (ignoreObjectConstruction) {
        return;
      }
      if (ignoreUncompilable && (expression.getArrayInitializer() != null || expression.getArrayDimensions().length > 0)) {
        //array creation can (almost) never be generic
        return;
      }
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      checkReferenceElement(classReference);
    }

    @Override
    public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
      if (!hasNeededLanguageLevel(typeElement)) {
        return;
      }
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      super.visitTypeElement(typeElement);
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(typeElement, PsiTypeElement.class);
      if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiClassObjectAccessExpression) {
        return;
      }
      if (ignoreTypeCasts && parent instanceof PsiTypeCastExpression) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(typeElement, PsiComment.class) != null) {
        return;
      }
      final PsiAnnotationMethod annotationMethod =
        PsiTreeUtil.getParentOfType(typeElement, PsiAnnotationMethod.class, true, PsiClass.class);
      if (ignoreUncompilable && annotationMethod != null) {
        // type of class type parameter cannot be parameterized if annotation method has default value
        final PsiAnnotationMemberValue defaultValue = annotationMethod.getDefaultValue();
        if (defaultValue != null && parent != annotationMethod) {
          return;
        }
      }
      if (parent instanceof PsiParameter && ignoreParametersOfOverridingMethods) {
        final PsiParameter parameter = (PsiParameter)parent;
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          if (MethodUtils.hasSuper(method)) {
            return;
          }
        }
      }
      final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
      checkReferenceElement(referenceElement);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      if (!hasNeededLanguageLevel(reference)) {
        return;
      }
      super.visitReferenceElement(reference);
      final PsiElement referenceParent = reference.getParent();
      if (!(referenceParent instanceof PsiReferenceList)) {
        return;
      }
      final PsiReferenceList referenceList = (PsiReferenceList)referenceParent;
      final PsiElement listParent = referenceList.getParent();
      if (!(listParent instanceof PsiClass)) {
        return;
      }
      checkReferenceElement(reference);
    }

    private void checkReferenceElement(PsiJavaCodeReferenceElement reference) {
      if (reference == null) {
        return;
      }
      final PsiType[] typeParameters = reference.getTypeParameters();
      if (typeParameters.length > 0) {
        return;
      }
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      final PsiElement qualifier = reference.getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement qualifierReference = (PsiJavaCodeReferenceElement)qualifier;
        if (!aClass.hasModifierProperty(PsiModifier.STATIC) && !aClass.isInterface() && !aClass.isEnum()) {
          checkReferenceElement(qualifierReference);
        }
      }
      if (!aClass.hasTypeParameters()) {
        return;
      }
      registerError(reference);
    }

    private boolean hasNeededLanguageLevel(PsiElement element) {
      return element.getLanguage().isKindOf(JavaLanguage.INSTANCE) && PsiUtil.isLanguageLevel5OrHigher(element);
    }
  }
}
