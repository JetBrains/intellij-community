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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RawUseOfParameterizedTypeInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreObjectConstruction = true;

  @SuppressWarnings("PublicField") public boolean ignoreTypeCasts = false;

  @SuppressWarnings("PublicField") public boolean ignoreUncompilable = false;

  @SuppressWarnings("PublicField") public boolean ignoreParametersOfOverridingMethods = false;

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "rawtypes";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "RawUseOfParameterized";
  }

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
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RawUseOfParameterizedTypeVisitor();
  }

  private class RawUseOfParameterizedTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
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
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      super.visitTypeElement(typeElement);
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(
        typeElement, PsiTypeElement.class, PsiReferenceParameterList.class, PsiJavaCodeReferenceElement.class);
      if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiClassObjectAccessExpression) {
        return;
      }
      if (ignoreTypeCasts && parent instanceof PsiTypeCastExpression) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(typeElement, PsiComment.class) != null) {
        return;
      }
      if (ignoreUncompilable && parent instanceof PsiAnnotationMethod) {
        // type of class type parameter cannot be parameterized if annotation method has default value
        final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)parent).getDefaultValue();
        if (defaultValue != null && typeElement.getParent() instanceof PsiTypeElement) {
          return;
        }
      }
      if (parent instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)parent;
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          if (ignoreParametersOfOverridingMethods) {
            if (MethodUtils.getSuper(method) != null || MethodCallUtils.isUsedAsSuperConstructorCallArgument(parameter, false)) {
              return;
            }
          }
          else if (ignoreUncompilable &&
                   (LibraryUtil.isOverrideOfLibraryMethod(method) || MethodCallUtils.isUsedAsSuperConstructorCallArgument(parameter, true))) {
            return;
          }
        }
      }
      final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
      checkReferenceElement(referenceElement);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
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
  }
}
