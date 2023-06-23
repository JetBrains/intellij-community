// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.ChronoUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringJoiner;


public class RedundantExplicitChronoFieldInspection extends AbstractBaseJavaLocalInspectionTool
  implements CleanupLocalInspectionTool {
  private static final CallMatcher CAN_BE_SIMPLIFIED_MATCHERS = CallMatcher.anyOf(
    ChronoUtil.CHRONO_GET_MATCHERS,
    ChronoUtil.CHRONO_WITH_MATCHERS,
    ChronoUtil.CHRONO_PLUS_MINUS_MATCHERS
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method == null) {
          return;
        }
        String methodName = method.getName();
        if (!"get".equals(methodName) && !"with".equals(methodName) &&
            !"plus".equals(methodName) && !"minus".equals(methodName)) {
          return;
        }
        if (!CAN_BE_SIMPLIFIED_MATCHERS.methodMatches(method)) return;
        int fieldArgumentIndex = 1;
        if ("get".equals(methodName) || "with".equals(methodName)) {
          fieldArgumentIndex = 0;
        }
        PsiExpression[] expressions = call.getArgumentList().getExpressions();
        if (expressions.length < fieldArgumentIndex + 1) {
          return;
        }
        @Nullable PsiExpression fieldExpression = expressions[fieldArgumentIndex];
        String chronoEnumName = getNameOfChronoEnum(fieldExpression, methodName);
        if (chronoEnumName == null) return;
        String newMethodName = getNewMethodName(chronoEnumName, call);
        if (newMethodName == null) return;
        PsiElement identifier = getIdentifier(call.getMethodExpression());
        if (identifier == null) return;
        holder.registerProblem(identifier,
                               InspectionGadgetsBundle.message("inspection.explicit.chrono.field.problem.descriptor"),
                               new InlineChronoEnumCallFix(newMethodName, fieldArgumentIndex));
      }

      @Nullable
      private static String getNewMethodName(@NotNull String chronoEnumName, @NotNull PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method == null) {
          return null;
        }
        if (!isAvailableCall(method, chronoEnumName)) {
          return null;
        }
        //'with(ChronoField, long)' can be converted only to 'with...(int)'
        if ("with".equals(method.getName())) {
          PsiType[] types = call.getArgumentList().getExpressionTypes();
          if (types.length != 2 || types[1] == null || TypeConversionUtil.getTypeRank(types[1]) > TypeConversionUtil.INT_RANK) {
            return null;
          }
        }
        String methodName = method.getName();
        return findEquivalentMethod(chronoEnumName, methodName);
      }

      private static boolean isAvailableCall(@NotNull PsiMethod method, @NotNull String chronoEnumName) {
        return switch (method.getName()) {
          case "get" -> ChronoUtil.isAnyGetSupported(method, ChronoUtil.getChronoField(chronoEnumName));
          case "with" -> ChronoUtil.isWithSupported(method, ChronoUtil.getChronoField(chronoEnumName));
          case "plus", "minus" -> ChronoUtil.isPlusMinusSupported(method, ChronoUtil.getChronoUnit(chronoEnumName));
          default -> false;
        };
      }

      @Nullable
      private static String findEquivalentMethod(@NotNull String chronoEnumName, @NotNull String methodName) {
        return switch (methodName) {
          case "plus" -> switch (chronoEnumName) {
            case "NANOS" -> "plusNanos";
            case "SECONDS" -> "plusSeconds";
            case "MINUTES" -> "plusMinutes";
            case "HOURS" -> "plusHours";
            case "DAYS" -> "plusDays";
            case "WEEKS" -> "plusWeeks";
            case "MONTHS" -> "plusMonths";
            case "YEARS" -> "plusYears";
            default -> null;
          };
          case "minus" -> switch (chronoEnumName) {
            case "NANOS" -> "minusNanos";
            case "SECONDS" -> "minusSeconds";
            case "MINUTES" -> "minusMinutes";
            case "HOURS" -> "minusHours";
            case "DAYS" -> "minusDays";
            case "WEEKS" -> "minusWeeks";
            case "MONTHS" -> "minusMonths";
            case "YEARS" -> "minusYears";
            default -> null;
          };
          case "get" -> switch (chronoEnumName) {
            case "NANO_OF_SECOND" -> "getNano";
            case "SECOND_OF_MINUTE" -> "getSecond";
            case "MINUTE_OF_HOUR" -> "getMinute";
            case "HOUR_OF_DAY" -> "getHour";
            case "DAY_OF_MONTH" -> "getDayOfMonth";
            case "DAY_OF_YEAR" -> "getDayOfYear";
            case "MONTH_OF_YEAR" -> "getMonth";
            case "YEAR" -> "getYear";
            default -> null;
          };
          case "with" -> switch (chronoEnumName) {
            case "NANO_OF_SECOND" -> "withNano";
            case "SECOND_OF_MINUTE" -> "withSecond";
            case "MINUTE_OF_HOUR" -> "withMinute";
            case "HOUR_OF_DAY" -> "withHour";
            case "DAY_OF_MONTH" -> "withDayOfMonth";
            case "DAY_OF_YEAR" -> "withDayOfYear";
            case "MONTH_OF_YEAR" -> "withMonth";
            case "YEAR" -> "withYear";
            default -> null;
          };
          default -> null;
        };
      }

      @Nullable
      private static PsiElement getIdentifier(@Nullable PsiReferenceExpression expression) {
        if (expression == null) return null;
        PsiIdentifier[] identifiers = PsiTreeUtil.getChildrenOfType(expression, PsiIdentifier.class);
        if (identifiers == null || identifiers.length != 1) {
          return null;
        }
        return identifiers[0];
      }

      @Nullable
      private static String getNameOfChronoEnum(@Nullable PsiExpression expression, @Nullable String methodName) {
        if (expression == null || methodName == null) return null;
        if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
          return null;
        }
        PsiElement resolvedElement = referenceExpression.resolve();
        if (!(resolvedElement instanceof PsiEnumConstant enumConstant)) {
          return null;
        }
        PsiClass containingClass = enumConstant.getContainingClass();
        if (containingClass == null || !containingClass.isEnum()) {
          return null;
        }
        String classQualifiedName = containingClass.getQualifiedName();
        if (!(ChronoUtil.CHRONO_FIELD.equals(classQualifiedName) && (methodName.equals("get") || methodName.equals("with"))) &&
            !(ChronoUtil.CHRONO_UNIT.equals(classQualifiedName)) && (methodName.equals("plus") || methodName.equals("minus"))) {
          return null;
        }
        return enumConstant.getName();
      }
    };
  }

  private static class InlineChronoEnumCallFix extends PsiUpdateModCommandQuickFix {
    private @NotNull final String myNewMethodName;
    private final int myDeletedArgumentIndex;

    InlineChronoEnumCallFix(@NotNull @NlsSafe String newMethodName, int deletedArgumentIndex) {
      myNewMethodName = newMethodName;
      myDeletedArgumentIndex = deletedArgumentIndex;
    }

    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x.call", myNewMethodName + "()");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.explicit.chrono.field.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      CommentTracker ct = new CommentTracker();
      String text = ct.text(qualifierExpression) + "." + myNewMethodName;
      PsiExpression[] expressions = call.getArgumentList().getExpressions();
      StringJoiner joiner = new StringJoiner(",", "(", ")");
      for (int i = 0; i < expressions.length; i++) {
        if (i == myDeletedArgumentIndex) {
          continue;
        }
        joiner.add(ct.text(expressions[i]));
      }
      text += joiner.toString();
      ct.replaceAndRestoreComments(call, text);
    }
  }
}
