// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public class ReturnNullInspection extends BaseInspection {

  private static final CallMatcher.Simple MAP_COMPUTE =
    CallMatcher.instanceCall("java.util.Map", "compute", "computeIfPresent", "computeIfAbsent");

  @SuppressWarnings("PublicField")
  public boolean m_reportObjectMethods = true;
  @SuppressWarnings("PublicField")
  public boolean m_reportArrayMethods = true;
  @SuppressWarnings("PublicField")
  public boolean m_reportCollectionMethods = true;
  @SuppressWarnings("PublicField")
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

    final PsiMethod method = PsiTreeUtil.getParentOfType(elt, PsiMethod.class, false, PsiLambdaExpression.class);
    if (method == null) return null;
    final PsiType type = method.getReturnType();
    if (TypeUtils.isOptional(type)) {
      // don't suggest to annotate Optional methods as Nullable
      return new ReplaceWithEmptyOptionalFix(((PsiClassType)type).rawType().getCanonicalText());
    }

    final NullableNotNullManager manager = NullableNotNullManager.getInstance(elt.getProject());
    return new DelegatingFix(new AddAnnotationPsiFix(manager.getDefaultNullable(), method,
                                                     ArrayUtilRt.toStringArray(manager.getNotNulls())));
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

    private @NonNls @NotNull String getReplacementText() {
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
      final PsiElement parent = ExpressionUtils.getPassThroughParent(value);
      if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiLambdaExpression)) {
        return;
      }
      final PsiElement element = PsiTreeUtil.getParentOfType(value, PsiMethod.class, PsiLambdaExpression.class);
      final PsiMethod method;
      final PsiType returnType;
      final boolean lambda;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
        returnType = method.getReturnType();
        lambda = false;
      }
      else if (element instanceof PsiLambdaExpression) {
        final PsiType functionalInterfaceType = ((PsiLambdaExpression)element).getFunctionalInterfaceType();
        method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
        lambda = true;
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
      if (lambda) {
        if (m_ignorePrivateMethods || isInNullableContext(element)) {
          return;
        }
      }
      else {
        if (m_ignorePrivateMethods && method.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass instanceof PsiAnonymousClass) {
          if (m_ignorePrivateMethods || isInNullableContext(containingClass.getParent())) {
            return;
          }
        }
      }
      final Project project = method.getProject();
      final NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(project).findEffectiveNullabilityInfo(method);
      if (info != null && info.getNullability() == Nullability.NULLABLE && !info.isInferred()) {
        return;
      }
      if (DfaPsiUtil.getTypeNullability(returnType) == Nullability.NULLABLE) {
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

    private boolean isInNullableContext(PsiElement element) {
      final PsiElement parent = element instanceof PsiExpression ? ExpressionUtils.getPassThroughParent((PsiExpression)element) : element;
      if (parent instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)parent;
        final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (codeBlock == null) {
          return false;
        }
        final PsiElement[] refs = DefUseUtil.getRefs(codeBlock, variable, element);
        return Arrays.stream(refs).anyMatch(this::isInNullableContext);
      }
      else if (parent instanceof PsiExpressionList) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
          return MAP_COMPUTE.test(methodCallExpression);
        }
      }
      return false;
    }
  }
}
