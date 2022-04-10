// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.siyeh.ig.callMatcher.CallMatcher.*;

public class SuspiciousDateFormatInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher PATTERN_METHODS = anyOf(
    instanceCall("java.text.SimpleDateFormat", "applyPattern", "applyLocalizedPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
    staticCall("java.time.format.DateTimeFormatter", "ofPattern"),
    instanceCall("java.time.format.DateTimeFormatterBuilder", "appendPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING)
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (PATTERN_METHODS.test(call)) {
          ExpressionUtils.nonStructuralChildren(call.getArgumentList().getExpressions()[0]).forEach(this::processExpression);
        }
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        if (ConstructionUtils.isReferenceTo(expression.getClassReference(), "java.text.SimpleDateFormat")) {
          PsiExpressionList args = expression.getArgumentList();
          if (args != null) {
            PsiExpression patternArg = ArrayUtil.getFirstElement(args.getExpressions());
            if (patternArg != null) {
              ExpressionUtils.nonStructuralChildren(patternArg).forEach(this::processExpression);
            }
          }
        }
      }

      private void processExpression(@NotNull PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression) {
          processLiteral((PsiLiteralExpression)expression);
        }
        if (expression instanceof PsiPolyadicExpression &&
            ((PsiPolyadicExpression)expression).getOperationTokenType().equals(JavaTokenType.PLUS)) {
          for (PsiExpression operand : ((PsiPolyadicExpression)expression).getOperands()) {
            ExpressionUtils.nonStructuralChildren(operand).forEach(this::processExpression);
          }
        }
      }

      private void processLiteral(@NotNull PsiLiteralExpression expression) {
        String pattern = ObjectUtils.tryCast(expression.getValue(), String.class);
        if (pattern == null) return;
        List<Token> tokens = new ArrayList<>();
        char lastChar = 0;
        int countNonFormat = 0;
        char[] array = pattern.toCharArray();
        boolean inQuote = false;
        for (int pos = 0; pos < array.length; pos++) {
          char c = array[pos];
          if (c == '\'') inQuote = !inQuote;
          if (c == lastChar) continue;
          lastChar = c;
          if (!inQuote && (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
            countNonFormat = 0;
            tokens.add(new Token(pos, array));
            continue;
          }
          countNonFormat++;
          if (countNonFormat > 3) {
            tokens.add(null);
          }
        }
        for (int i = 0; i < tokens.size(); i++) {
          Token token = tokens.get(i);
          Token prev = i > 0 ? tokens.get(i - 1) : null;
          Token next = i < tokens.size() - 1 ? tokens.get(i + 1) : null;
          Problem problem = getProblem(token, prev, next);
          if (problem != null) {
            TextRange range = ExpressionUtils.findStringLiteralRange(expression, token.pos, token.pos + token.length);
            if (range != null) {
              holder.registerProblem(expression, range, problem.toString(), new IncorrectDateFormatFix(token, range));
            }
          }
        }
      }

      @Contract("null, _, _ -> null")
      private Problem getProblem(@Nullable Token token, @Nullable Token prev, @Nullable Token next) {
        if (token == null) return null;
        switch (token.character) {
          case 'Y':
            if (!hasNeighbor("w", prev, next)) {
              return new Problem(token, "week year", "year");
            }
            break;
          case 'M':
            if (hasNeighbor("HhKk", prev, next) && !hasNeighbor("yd", prev, next)) {
              return new Problem(token, "month", "minute");
            }
            break;
          case 'm':
            if (hasNeighbor("yd", prev, next) && !hasNeighbor("HhKk", prev, next)) {
              return new Problem(token, "minute", "month");
            }
            break;
          case 'D':
            if (hasNeighbor("ML", prev, next)) {
              return new Problem(token, "day of year", "day of month");
            }
            break;
          case 'S':
            if (hasNeighbor("m", prev, next)) {
              return new Problem(token, "milliseconds", "seconds");
            }
            break;
        }
        return null;
      }

      private boolean hasNeighbor(@NotNull @NonNls String neighbors, @Nullable Token prev, @Nullable Token next) {
        return prev != null && neighbors.indexOf(prev.character) >= 0 ||
               next != null && neighbors.indexOf(next.character) >= 0;
      }
    };
  }

  private static class Token {
    final char character;
    final int pos;
    final int length;

    Token(int pos, char[] chars) {
      this.character = chars[pos];
      this.pos = pos;
      int length = 0;
      for (int i = pos; i < chars.length && chars[i] == character; i++) {
        length++;
      }
      this.length = length;
    }

    public String fixed() {
      return Character.isUpperCase(character) ? toString().toLowerCase(Locale.ROOT) : toString().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
      return StringUtil.repeat(String.valueOf(character), length);
    }
  }

  private static final class Problem {
    final Token token;
    final @NlsSafe String usedName;
    final @NlsSafe String intendedName;

    private Problem(Token token, @NlsSafe String usedName, @NlsSafe String intendedName) {
      this.token = token;
      this.usedName = usedName;
      this.intendedName = intendedName;
    }

    @Override
    public @InspectionMessage String toString() {
      String key = Character.isUpperCase(token.character) ? "inspection.suspicious.date.format.message.upper"
                                                          : "inspection.suspicious.date.format.message.lower";
      return InspectionGadgetsBundle.message(key, token, usedName, token.fixed(), intendedName);
    }
  }

  private static class IncorrectDateFormatFix implements LocalQuickFix {
    @SafeFieldForPreview
    private final Token myToken;
    @SafeFieldForPreview
    private final TextRange myRange;

    IncorrectDateFormatFix(Token token, TextRange range) {
      myToken = token;
      myRange = range;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", myToken.toString(), myToken.fixed());
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("incorrect.date.format.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLiteralExpression literal = ObjectUtils.tryCast(descriptor.getStartElement(), PsiLiteralExpression.class);
      if (literal == null) return;
      String text = literal.getText();
      if (myRange.getEndOffset() >= text.length()) return;
      String existing = myRange.substring(text);
      if (!existing.equals(myToken.toString())) return;
      text = text.substring(0, myRange.getStartOffset()) + myToken.fixed() + text.substring(myRange.getEndOffset());
      PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(text, literal);
      literal.replace(replacement);
    }
  }
}
