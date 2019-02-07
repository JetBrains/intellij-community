/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.AnnotateMethodFix;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ReturnNullInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean m_reportObjectMethods = true;
  @SuppressWarnings({"PublicField"})
  public boolean m_reportArrayMethods = true;
  @SuppressWarnings({"PublicField"})
  public boolean m_reportCollectionMethods = true;
  @SuppressWarnings({"PublicField"})
  public boolean m_ignorePrivateMethods = false;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("return.of.null.ignore.private.option"), "m_ignorePrivateMethods");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("return.of.null.arrays.option"), "m_reportArrayMethods");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("return.of.null.collections.option"), "m_reportCollectionMethods");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("return.of.null.objects.option"), "m_reportObjectMethods");
    optionsPanel.addComponent(NullableNotNullDialog.createConfigureAnnotationsButton(optionsPanel));
    return optionsPanel;
  }

  @Override
  @Pattern("[a-zA-Z_0-9.-]+")
  @NotNull
  public String getID() {
    return "ReturnOfNull";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("return.of.null.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "return.of.null.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement elt = (PsiElement)infos[0];
    if (!AnnotationUtil.isAnnotatingApplicable(elt)) {
      return null;
    }

    final PsiElement element = PsiTreeUtil.getParentOfType(elt, PsiMethod.class, PsiLambdaExpression.class);
    if (element instanceof PsiLambdaExpression) {
      return null;
    }
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiType type = method.getReturnType();
      if (TypeUtils.isOptional(type)) {
        // don't suggest to annotate Optional methods as Nullable
        return new ReplaceWithEmptyOptionalFix(((PsiClassType)type).rawType().getCanonicalText());
      }
    }

    final NullableNotNullManager manager = NullableNotNullManager.getInstance(elt.getProject());
    return new DelegatingFix(new AnnotateMethodFix(
      manager.getDefaultNullable(),
      ArrayUtil.toStringArray(manager.getNotNulls())));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnNullVisitor();
  }

  private static class ReplaceWithEmptyOptionalFix extends InspectionGadgetsFix {

    private final String myTypeText;

    ReplaceWithEmptyOptionalFix(String typeText) {
      myTypeText = typeText;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", getReplacementText());
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Optional.empty()");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiLiteralExpression)) {
        return;
      }
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      PsiReplacementUtil.replaceExpression(literalExpression, getReplacementText());
    }

    @NotNull
    private String getReplacementText() {
      return myTypeText + "." + (OptionalUtil.GUAVA_OPTIONAL.equals(myTypeText) ? "absent" : "empty") + "()";
    }
  }

  private class ReturnNullVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression value) {
      super.visitLiteralExpression(value);
      final String text = value.getText();
      if (!PsiKeyword.NULL.equals(text)) {
        return;
      }
      PsiElement parent = value.getParent();
      while (parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression ||
             parent instanceof PsiTypeCastExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiReturnStatement)) {
        return;
      }
      final PsiElement element = PsiTreeUtil.getParentOfType(value, PsiMethod.class, PsiLambdaExpression.class);
      final PsiMethod method;
      final PsiType returnType;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
        returnType = method.getReturnType();
      } 
      else if (element instanceof PsiLambdaExpression) {
        final PsiType functionalInterfaceType = ((PsiLambdaExpression)element).getFunctionalInterfaceType();
        method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
      } 
      else {
        return;
      }
      if (method == null || returnType == null) {
        return;
      }

      if (TypeUtils.isOptional(returnType)) {
        registerError(value, value);
        return;
      }

      if (m_ignorePrivateMethods && method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }

      if (NullableNotNullManager.isNullable(method)) {
        return;
      }
      if (CollectionUtils.isCollectionClassOrInterface(returnType)) {
        if (m_reportCollectionMethods) {
          registerError(value, value);
        }
      }
      else if (returnType.getArrayDimensions() > 0) {
        if (m_reportArrayMethods) {
          registerError(value, value);
        }
      }
      else if (!returnType.equalsToText("java.lang.Void")){
        if (m_reportObjectMethods) {
          registerError(value, value);
        }
      }
    }
  }
}
