// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class EqualsOnSuspiciousObjectInspection extends BaseInspection {
  private final Map<String, ReplaceInfo> myClasses =
    Map.ofEntries(
      Map.entry(CommonClassNames.JAVA_LANG_STRING_BUILDER, ReplaceInfo.available(true, "toString")),
      Map.entry(CommonClassNames.JAVA_LANG_STRING_BUFFER, ReplaceInfo.available(true, "toString")),
      Map.entry("java.util.concurrent.atomic.AtomicBoolean", ReplaceInfo.available(false, "get")),
      Map.entry("java.util.concurrent.atomic.AtomicInteger", ReplaceInfo.available(false, "get")),
      Map.entry("java.util.concurrent.atomic.AtomicIntegerArray", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.AtomicLong", ReplaceInfo.available(false, "get")),
      Map.entry("java.util.concurrent.atomic.AtomicLongArray", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.AtomicReference", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.DoubleAccumulator", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.DoubleAdder", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.LongAccumulator", ReplaceInfo.notAvailable()),
      Map.entry("java.util.concurrent.atomic.LongAdder", ReplaceInfo.notAvailable()));

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    String typeName = (String)infos[0];
    return InspectionGadgetsBundle.message("equals.called.on.suspicious.object.problem.descriptor", StringUtil.getShortName(typeName));
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    PsiReferenceExpression expression = (PsiReferenceExpression)infos[1];
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    PsiType qualifierType = qualifierExpression.getType();
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiMethodCallExpression callExpression)) {
      return null;
    }
    if (!BaseEqualsVisitor.OBJECT_EQUALS.matches(callExpression)) {
      return null;
    }
    PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (arguments.length != 1) {
      return null;
    }
    PsiExpression argument = arguments[0];
    if (argument == null) {
      return null;
    }
    PsiType argumentType = argument.getType();
    if (argumentType == null || !argumentType.equals(qualifierType)) {
      return null;
    }
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(argumentType);
    if (psiClass == null) {
      return null;
    }
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }
    ReplaceInfo info = myClasses.get(qualifiedName);
    if (info == null || !info.available) {
      return null;
    }

    return new EqualsOnSuspiciousObjectFix(info);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseEqualsVisitor() {
      @Override
      boolean checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType type1, @NotNull PsiType type2) {
        if (checkType(expression, type1)) return true;
        if (checkType(expression, type2)) return true;
        return false;
      }

      private boolean checkType(PsiReferenceExpression expression, PsiType type) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (psiClass != null) {
          String qualifiedName = psiClass.getQualifiedName();
          if (qualifiedName != null && myClasses.containsKey(qualifiedName)) {
            PsiElement name = expression.getReferenceNameElement();
            registerError(name == null ? expression : name, qualifiedName, expression);
            return true;
          }
        }
        return false;
      }
    };
  }

  private record ReplaceInfo(boolean available, boolean isObjects, @Nullable String valueMethod) {
    private static ReplaceInfo notAvailable() {
      return new ReplaceInfo(false, false, null);
    }

    private static ReplaceInfo available(boolean isObjects, @NotNull String valueMethod) {
      return new ReplaceInfo(true, isObjects, valueMethod);
    }
  }

  private static class EqualsOnSuspiciousObjectFix extends InspectionGadgetsFix {
    @SafeFieldForPreview
    private final ReplaceInfo myInfo;

    private EqualsOnSuspiciousObjectFix(ReplaceInfo info) {
      myInfo = info;
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("equals.called.on.suspicious.object.fix.name", myInfo.valueMethod);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("equals.called.on.suspicious.object.fix.family.name");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();

      if (psiElement == null || !(psiElement.getParent() instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiMethodCallExpression callExpression)) {
        return;
      }
      PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
      if (expressions.length != 1) {
        return;
      }
      PsiExpression argument = expressions[0];
      if (argument == null) {
        return;
      }
      boolean argumentIsNullable = isNullable(argument);
      StringBuilder builder = new StringBuilder();
      CommentTracker ct = new CommentTracker();

      if (argumentIsNullable) {
        builder.append(ct.text(argument, ParenthesesUtils.EQUALITY_PRECEDENCE))
          .append("!=null &&");
      }

      String qualifierText = ct.text(qualifierExpression, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + "." + myInfo.valueMethod + "()";
      String argumentText = ct.text(argument, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + "." + myInfo.valueMethod + "()";
      if (myInfo.isObjects) {
        builder.append(qualifierText).append(".equals(").append(argumentText).append(")");
      }
      else {
        builder.append(qualifierText).append("==").append(argumentText);
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression newExpression = factory.createExpressionFromText(builder.toString(), callExpression);
      PsiElement callParent = callExpression.getParent();
      if (callParent instanceof PsiExpression &&
          ParenthesesUtils.areParenthesesNeeded(newExpression, (PsiExpression)callParent, true)) {
        newExpression = factory.createExpressionFromText("(" + newExpression.getText() + ")", callExpression);
      }
      PsiElement replaced = ct.replaceAndRestoreComments(callExpression, newExpression);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
      styleManager.reformat(replaced);
    }

    private static boolean isNullable(@NotNull PsiExpression argument) {
      final Nullability nullability = NullabilityUtil.getExpressionNullability(argument, true);
      if (nullability == Nullability.NULLABLE) return true;
      if (argument instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiModifierListOwner) {
          if (NullableNotNullManager.isNullable((PsiModifierListOwner)target)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
