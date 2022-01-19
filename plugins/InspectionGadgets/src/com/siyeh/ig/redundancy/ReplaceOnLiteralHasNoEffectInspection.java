// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.intellij.util.ObjectUtils.tryCast;

public class ReplaceOnLiteralHasNoEffectInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher STRING_REPLACE = CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING,
                                                                                  "replace", "replaceFirst", "replaceAll");
  private static final int MAX_QUALIFIER_LENGTH = 1000;
  private static final int MAX_PATTERN_LENGTH = 200;

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!STRING_REPLACE.test(call)) return;
        PsiLiteralExpression qualifier = tryCast(
          PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()), PsiLiteralExpression.class);
        if (qualifier == null || qualifier.getTextLength() > MAX_QUALIFIER_LENGTH) return;
        String str = tryCast(qualifier.getValue(), String.class);
        PsiExpression pattern = PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
        String name = call.getMethodExpression().getReferenceName();
        if (!isRedundant(str, pattern, "replace".equals(name))) return;
        PsiElement refName = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
        holder.registerProblem(call, InspectionGadgetsBundle.message("inspection.replace.on.literal.display.name"),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                               TextRange.create(refName.getTextRangeInParent().getStartOffset(), call.getTextLength()),
                               ExpressionUtils.isVoidContext(call) ? null : new ReplaceWithQualifierFix());
      }

      private boolean isRedundant(String str, PsiExpression pattern, boolean literalMatch) {
        Object constValue = ExpressionUtils.computeConstantExpression(pattern);
        if (constValue != null) {
          return isRedundantLiteralMatch(str, constValue, literalMatch);
        }
        if (literalMatch && pattern instanceof PsiPolyadicExpression &&
            ((PsiPolyadicExpression)pattern).getOperationTokenType().equals(JavaTokenType.PLUS)) {
          PsiExpression[] operands = ((PsiPolyadicExpression)pattern).getOperands();
          boolean stringType = false;
          for (int i = 0; i < operands.length; i++) {
            PsiExpression operand = operands[i];
            stringType = stringType || TypeUtils.isJavaLangString(operand.getType());
            if (!stringType && (i > 0 || !TypeUtils.isJavaLangString(operands[1].getType()))) continue;
            constValue = ExpressionUtils.computeConstantExpression(operand);
            if (constValue == null) continue;
            if (isRedundantLiteralMatch(str, constValue, true)) return true;
          }
        }
        return false;
      }

      private boolean isRedundantLiteralMatch(String str, Object value, boolean literalMatch) {
        String patternValue;
        if (value instanceof String) {
          patternValue = (String)value;
        } else if (value instanceof Character) {
          patternValue = value.toString();
        } else {
          return false;
        }
        if (literalMatch) return !str.contains(patternValue);
        if (patternValue.length() > MAX_PATTERN_LENGTH) return false;
        Pattern regex;
        try {
          regex = Pattern.compile(patternValue);
        }
        catch (PatternSyntaxException e) {
          return false;
        }
        return !regex.matcher(str).find();
      }
    };
  }

  private static class ReplaceWithQualifierFix implements LocalQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.string.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project,
                         @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = tryCast(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      new CommentTracker().replace(call, qualifier);
    }
  }
}
