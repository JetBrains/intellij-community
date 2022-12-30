// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RedundantEscapeInRegexReplacementInspection extends BaseInspection {
  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    char c = (char)infos[0];
    return InspectionGadgetsBundle.message("redundant.escape.in.regex.replacement.problem.descriptor", c);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantEscapeInRegexReplacementVisitor();
  }

  private static class RedundantEscapeInRegexReplacementVisitor extends BaseInspectionVisitor {

    private static final CallMatcher REGEX_REPLACEMENT_METHODS = CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_STRING, "replaceAll", "replaceFirst"),
      CallMatcher.exactInstanceCall("java.util.regex.Matcher", "appendReplacement"),
      CallMatcher.exactInstanceCall("java.util.regex.Matcher", "replaceAll", "replaceFirst")
        .parameterTypes(CommonClassNames.JAVA_LANG_STRING)
    );

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!REGEX_REPLACEMENT_METHODS.matches(expression)) {
        return;
      }
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      final Object value = ExpressionUtils.computeConstantExpression(lastArgument);
      if (!(value instanceof String)) {
        return;
      }
      final String string = (String)value;
      boolean escaped = false;
      for (int i = 0, length = string.length(); i < length; i++) {
        char c = string.charAt(i);
        if (c == '\\') {
          escaped = !escaped;
        }
        else {
          if (escaped) {
            escaped = false;
            if (c == '$') continue;
            final TextRange range = ExpressionUtils.findStringLiteralRange(lastArgument, i - 1, i);
            if (range != null) {
              registerErrorAtOffset(lastArgument, range.getStartOffset(), range.getLength(), c);
            }
            else {
              registerError(lastArgument, c);
              return;
            }
          }
        }
      }
    }
  }
}
