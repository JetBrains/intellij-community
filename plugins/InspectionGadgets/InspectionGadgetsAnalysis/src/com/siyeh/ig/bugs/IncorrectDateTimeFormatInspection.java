// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import static com.siyeh.ig.callMatcher.CallMatcher.*;

public class IncorrectDateTimeFormatInspection extends AbstractBaseJavaLocalInspectionTool {

  private static IntPredicate rangeOf(int from, int to) {
    return count -> count >= from && count <= to;
  }

  private static IntPredicate setOf(int... numbers) {
    return  count -> IntOpenHashSet.of(numbers).contains(count);
  }

  private static final Map<Character, IntPredicate> DATE_TIME_FORMATTER_ALLOWED = Map.ofEntries(
    Map.entry('G', rangeOf(1, 5)),
    Map.entry('u', rangeOf(1, 19)),
    Map.entry('y', rangeOf(1, 19)),
    //according to the specification, must not exceed 19 but current implementation doesn't restrict this
    Map.entry('Y', rangeOf(1, Integer.MAX_VALUE)),
    Map.entry('Q', rangeOf(1, 5)),
    Map.entry('q', rangeOf(1, 5)),
    Map.entry('M', rangeOf(1, 5)),
    Map.entry('L', rangeOf(1, 5)),
    Map.entry('w', rangeOf(1, 2)),
    Map.entry('W', rangeOf(1, 1)),
    Map.entry('d', rangeOf(1, 2)),
    Map.entry('D', rangeOf(1, 3)),
    Map.entry('F', rangeOf(1, 1)),
    Map.entry('g', rangeOf(1, 19)),
    Map.entry('E', rangeOf(1, 5)),
    Map.entry('e', rangeOf(1, 5)),
    Map.entry('c', rangeOf(1, 5)),

    Map.entry('a', rangeOf(1, 1)),
    Map.entry('h', rangeOf(1, 2)),
    Map.entry('H', rangeOf(1, 2)),
    Map.entry('k', rangeOf(1, 2)),
    Map.entry('K', rangeOf(1, 2)),
    Map.entry('m', rangeOf(1, 2)),
    Map.entry('s', rangeOf(1, 2)),
    Map.entry('S', rangeOf(1, 9)), //limit as nanos
    Map.entry('A', rangeOf(1, 19)),
    Map.entry('n', rangeOf(1, 19)),
    Map.entry('N', rangeOf(1, 19)),

    Map.entry('B', setOf(1, 4, 5)),

    Map.entry('V', rangeOf(2, 2)),
    Map.entry('v', setOf(1, 4)),
    Map.entry('z', rangeOf(1, 4)),
    Map.entry('O', setOf(1, 4)),
    Map.entry('X', rangeOf(1, 5)),
    Map.entry('x', rangeOf(1, 5)),
    Map.entry('Z', rangeOf(1, 5))
  );

  private static final CallMatcher PATTERN_METHODS = anyOf(
    staticCall("java.time.format.DateTimeFormatter", "ofPattern"),
    instanceCall("java.time.format.DateTimeFormatterBuilder", "appendPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING)
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (PATTERN_METHODS.test(call)) {
          processExpression(call.getArgumentList().getExpressions()[0]);
        }
      }

      private void processExpression(@NotNull PsiExpression expression) {
        Object patternObject = ExpressionUtils.computeConstantExpression(expression);
        if (!(patternObject instanceof String)) return;
        String pattern = (String)patternObject;
        List<Token> tokens = new ArrayList<>();
        int optionalDepth = 0;
        char[] array = pattern.toCharArray();
        boolean inQuote = false;
        int lastQuoteIndex = 0;
        int length = 0;
        for (int pos = 0; pos < array.length; pos++) {
          char c = array[pos];

          //process tokens
          if (!inQuote && isLetter(c)) {
            length++;
            if (pos != array.length - 1 && c == array[pos + 1]) {
              continue;
            }
            else {
              tokens.add(new Token(array, pos - length + 1, length));
              checkPadding(expression, array, pos - length + 1, pos + 1);
            }
          }
          length = 0;


          //check if it starts quote region or one quote
          //case with one quote ('') is processed as quote region
          if (c == '\'') {
            inQuote = !inQuote;
            lastQuoteIndex = pos;
            continue;
          }
          //skip whole region in quote
          if (inQuote) {
            continue;
          }

          checkUnsupportedSymbolsProblem(expression, pos, c);

          if (c == '[') {
            optionalDepth++;
          }
          if (c == ']') {
            optionalDepth--;
            checkOptionalDepthProblem(expression, optionalDepth, pos);
            if (optionalDepth < 0) {
              //reset after problem
              optionalDepth = 0;
            }
          }
        }

        checkInQuoteProblem(expression, inQuote, lastQuoteIndex);

        for (Token token : tokens) {
          checkAvailableCount(expression, token, holder);
        }
      }

      private void checkOptionalDepthProblem(@NotNull PsiExpression expression, int optionalDepth, int pos) {
        if (optionalDepth < 0) {
          TextRange range = ExpressionUtils.findStringLiteralRange(expression, pos, pos + 1);
          holder.registerProblem(expression, range,
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.unpaired", ']'),
                                 LocalQuickFix.EMPTY_ARRAY);
        }
      }

      private void checkInQuoteProblem(@NotNull PsiExpression expression, boolean inQuote, int lastQuoteIndex) {
        if (inQuote) {
          TextRange range = ExpressionUtils.findStringLiteralRange(expression, lastQuoteIndex, lastQuoteIndex + 1);
          holder.registerProblem(expression, range,
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.unpaired", '\''),
                                 LocalQuickFix.EMPTY_ARRAY);
        }
      }

      private void checkUnsupportedSymbolsProblem(@NotNull PsiExpression expression, int pos, char c) {
        if (c == '{' || c == '}' || c == '#') {
          TextRange range = ExpressionUtils.findStringLiteralRange(expression, pos, pos + 1);
          holder.registerProblem(expression, range,
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.unsupported", c),
                                 LocalQuickFix.EMPTY_ARRAY);
        }
      }

      private void checkPadding(@NotNull PsiExpression expression, char[] array, int firstLetterIndex, int nextAfterP) {
        if (array[firstLetterIndex] == 'p') {
          if (nextAfterP >= array.length || !isLetter(array[nextAfterP])) {
            TextRange range = ExpressionUtils.findStringLiteralRange(expression, firstLetterIndex, nextAfterP);
            holder.registerProblem(expression, range,
                                   InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.padding",
                                                                   array[firstLetterIndex]),
                                   LocalQuickFix.EMPTY_ARRAY);
          }
        }
      }

      private boolean isLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
      }

      private void checkAvailableCount(PsiExpression expression, @Nullable Token token, ProblemsHolder holder) {
        if (token == null) {
          return;
        }
        if (token.character == 'p') {
          //checked during tokenizing
          return;
        }
        IntPredicate verifier = DATE_TIME_FORMATTER_ALLOWED.get(token.character);
        if (verifier == null || !verifier.test(token.length)) {
          TextRange range = ExpressionUtils.findStringLiteralRange(expression, token.pos, token.pos + token.length);
          holder.registerProblem(expression, range,
                                 InspectionGadgetsBundle.message("inspection.incorrect.date.format.message.unsupported",
                                                                 token.toString()),
                                 LocalQuickFix.EMPTY_ARRAY);
        }
      }
    };
  }

  private static class Token {
    final char character;
    final int pos;
    final int length;

    Token(char[] chars, int pos, int length) {
      this.character = chars[pos];
      this.pos = pos;
      this.length = length;
    }

    @Override
    public String toString() {
      return StringUtil.repeat(String.valueOf(character), length);
    }
  }
}
