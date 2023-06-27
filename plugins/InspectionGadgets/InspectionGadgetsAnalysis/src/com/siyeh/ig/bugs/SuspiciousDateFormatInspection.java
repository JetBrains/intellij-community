// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.util.ChronoUtil;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SuspiciousDateFormatInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        if (ChronoUtil.isPatternForDateFormat(expression)) {
          processLiteral(expression);
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
      private static Problem getProblem(@Nullable Token token, @Nullable Token prev, @Nullable Token next) {
        if (token == null) return null;
        switch (token.character) {
          case 'Y' -> {
            if (!hasNeighbor("w", prev, next)) {
              return new Problem(token, "week year", "year");
            }
          }
          case 'M' -> {
            if (hasNeighbor("HhKk", prev, next) && !hasNeighbor("yd", prev, next)) {
              return new Problem(token, "month", "minute");
            }
          }
          case 'm' -> {
            if (hasNeighbor("yd", prev, next) && !hasNeighbor("HhKk", prev, next)) {
              return new Problem(token, "minute", "month");
            }
          }
          case 'D' -> {
            if (hasNeighbor("ML", prev, next)) {
              return new Problem(token, "day of year", "day of month");
            }
          }
          case 'S' -> {
            if (hasNeighbor("m", prev, next)) {
              return new Problem(token, "milliseconds", "seconds");
            }
          }
        }
        return null;
      }

      private static boolean hasNeighbor(@NotNull @NonNls String neighbors, @Nullable Token prev, @Nullable Token next) {
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

  private record Problem(Token token, @NlsSafe String usedName, @NlsSafe String intendedName) {
    @Override
    public @InspectionMessage String toString() {
      String key = Character.isUpperCase(token.character) ? "inspection.suspicious.date.format.message.upper"
                                                          : "inspection.suspicious.date.format.message.lower";
      return InspectionGadgetsBundle.message(key, token, usedName, token.fixed(), intendedName);
    }
  }

  private static class IncorrectDateFormatFix extends PsiUpdateModCommandQuickFix {
    private final Token myToken;
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
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiLiteralExpression literal = ObjectUtils.tryCast(element, PsiLiteralExpression.class);
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
