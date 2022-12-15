// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.Stack;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.bugs.FormatDecode;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.siyeh.ig.callMatcher.CallMatcher.*;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

/**
 * @author Bas Leijdekkers
 */
public class PlaceholderCountMatchesArgumentCountInspection extends BaseInspection {

  private static final CallMatcher FORMATTED_LOG4J =
    staticCall("org.apache.logging.log4j.LogManager", "getFormatterLogger");
  private static final CallMatcher SLF4J_BUILDER = instanceCall("org.slf4j.Logger", "atError", "atDebug",
                                                              "atInfo", "atLevel", "atWarn", "atTrace");

  private static final LoggerTypeSearcher SLF4J_HOLDER = new LoggerTypeSearcher() {

    @Override
    public LoggerType findType(PsiMethodCallExpression expression, LoggerContext context) {
      if (context.ignoreSlf4jThrowableHavePlaceholder) {
        //use old style as more common
        return LoggerType.LOG4J_OLD_STYLE;
      }
      return LoggerType.SLF4J;
    }
  };

  private static final LoggerTypeSearcher SLF4J_HOLDER_BUILDER = new LoggerTypeSearcher() {

    @Override
    public LoggerType findType(PsiMethodCallExpression expression, LoggerContext context) {
      if (context.ignoreSlf4jThrowableHavePlaceholder) {
        return LoggerType.EQUAL_PLACEHOLDERS;
      }
      PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
      if(SLF4J_BUILDER.matches(qualifierExpression)){
        return LoggerType.SLF4J;
      }
      //otherwise it is too flexible to solve
      return null;
    }
  };

  private static final LoggerTypeSearcher LOG4J_HOLDER = new LoggerTypeSearcher() {
    @Override
    public LoggerType findType(PsiMethodCallExpression expression, LoggerContext context) {
      final PsiExpression qualifierExpression =
        PsiUtil.skipParenthesizedExprDown(expression.getMethodExpression().getQualifierExpression());

      PsiExpression initializer = null;
      if (qualifierExpression instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable variable)) {
          return null;
        }
        //for lombok
        if (!variable.isPhysical()) {
          return LoggerType.LOG4J_OLD_STYLE;
        }

        if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
          return null;
        }

        initializer = variable.getInitializer();
        if (initializer == null) return null;
      }
      else if (qualifierExpression instanceof PsiMethodCallExpression psiMethodCallExpression) {
        initializer = psiMethodCallExpression;
      }

      return initializer instanceof PsiCallExpression callExpression && FORMATTED_LOG4J.matches(callExpression) ?
             LoggerType.LOG4J_FORMATTED_STYLE : LoggerType.LOG4J_OLD_STYLE;
    }
  };

  private static final CallMapper<LoggerTypeSearcher>
    LOGGER_TYPE_SEARCHERS = new CallMapper<LoggerTypeSearcher>()
    .register(instanceCall("org.slf4j.Logger", "trace", "debug", "info", "warn", "error"), SLF4J_HOLDER)
    .register(instanceCall("org.slf4j.spi.LoggingEventBuilder", "log"), SLF4J_HOLDER_BUILDER)
    .register(instanceCall("org.apache.logging.log4j.Logger", "trace", "debug", "info", "warn", "error", "fatal", "log"), LOG4J_HOLDER)
    .register(instanceCall("org.apache.logging.log4j.LogBuilder", "log"),
              (PsiMethodCallExpression ex, LoggerContext context) -> LoggerType.EQUAL_PLACEHOLDERS);

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Result result = (Result)infos[0];
    if (result.result == ResultType.INCORRECT_STRING) {
      return InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.incorrect.problem.descriptor");
    }
    return (result.argumentCount() > result.placeholderCount())
           ? InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.more.problem.descriptor",
                                             result.argumentCount(), result.placeholderCount())
           : InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.fewer.problem.descriptor",
                                             result.argumentCount(), result.placeholderCount());
  }

  @SuppressWarnings("PublicField")
  public boolean ignoreSlf4jThrowableHavePlaceholder = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSlf4jThrowableHavePlaceholder", InspectionGadgetsBundle.message(
        "placeholder.count.matches.argument.count.slf4j.throwable.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PlaceholderCountMatchesArgumentCountVisitor();
  }

  private class PlaceholderCountMatchesArgumentCountVisitor extends BaseInspectionVisitor {

    private static final int MAX_PROCESSED_VARIABLES = 5;

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);

      LoggerTypeSearcher holder = LOGGER_TYPE_SEARCHERS.mapFirst(expression);
      if (holder == null) return;
      LoggerType loggerType = holder.findType(expression, new LoggerContext(ignoreSlf4jThrowableHavePlaceholder));
      if (loggerType == null) return;
      PsiMethod method = expression.resolveMethod();
      if (method == null) return;
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 0) {
        return;
      }
      final int index;
      if (!TypeUtils.isJavaLangString(parameters[0].getType())) {
        if (parameters.length < 2 || !TypeUtils.isJavaLangString(parameters[1].getType())) {
          return;
        }
        index = 2;
      }
      else {
        index = 1;
      }
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      int argumentCount = arguments.length - index;
      boolean lastArgumentIsException = hasThrowableType(arguments[arguments.length - 1]);
      if (argumentCount == 1) {
        final PsiExpression argument = arguments[index];
        final PsiType argumentType = argument.getType();
        if (argumentType instanceof PsiArrayType) {
          if (argumentType.equalsToText("java.lang.Object[]") && argument instanceof final PsiNewExpression newExpression) {
            final PsiArrayInitializerExpression arrayInitializerExpression = newExpression.getArrayInitializer();
            if (arrayInitializerExpression != null) {
              final PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
              argumentCount = initializers.length;
              lastArgumentIsException = initializers.length > 0 && hasThrowableType(initializers[initializers.length - 1]);
            }
            else {
              return;
            }
          }
          else {
            return;
          }
        }
      }
      final PsiExpression logStringArgument = arguments[index - 1];
      String text = buildString(logStringArgument);
      if (text == null) return;

      Integer placeholderCount = solvePlaceholderCount(loggerType, argumentCount, text);
      if (placeholderCount == null) {
        registerError(logStringArgument, new Result(argumentCount, 0, ResultType.INCORRECT_STRING));
        return;
      }

      ResultType resultType = switch (loggerType) {
        case SLF4J -> {
          //according to the reference an exception should not have a placeholder
          argumentCount = lastArgumentIsException ? argumentCount - 1 : argumentCount;
          yield (placeholderCount == argumentCount) ? ResultType.SUCCESS : ResultType.PLACE_HOLDER_MISMATCH;
        }
        case EQUAL_PLACEHOLDERS -> placeholderCount == argumentCount ? ResultType.SUCCESS : ResultType.PLACE_HOLDER_MISMATCH;
        case LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE -> {
          // if there is more than one argument and the last argument is an exception, but there is a placeholder for
          // the exception, then the stack trace won't be logged.
          ResultType type =
            ((placeholderCount == argumentCount && (!lastArgumentIsException || argumentCount > 1)) ||
             (lastArgumentIsException && placeholderCount == argumentCount - 1)) ?
            ResultType.SUCCESS : ResultType.PLACE_HOLDER_MISMATCH;
          argumentCount = lastArgumentIsException ? argumentCount - 1 : argumentCount;
          yield type;
        }
      };

      if (resultType == ResultType.SUCCESS) {
        return;
      }

      registerError(logStringArgument, new Result(argumentCount, placeholderCount, resultType));
    }

    @Nullable
    private static Integer solvePlaceholderCount(LoggerType loggerType, int argumentCount, String text) {
      int placeholderCount;
      if (loggerType == LoggerType.LOG4J_FORMATTED_STYLE) {
        FormatDecode.Validator[] validators;
        try {
          validators = FormatDecode.decode(text, argumentCount);
        }
        catch (FormatDecode.IllegalFormatException e) {
          return null;
        }
        placeholderCount = validators.length;
      }
      else {
        placeholderCount = countPlaceholders(text);
      }
      return placeholderCount;
    }

    private static boolean hasThrowableType(PsiExpression lastArgument) {
      final PsiType type = lastArgument.getType();
      if (type instanceof final PsiDisjunctionType disjunctionType) {
        for (PsiType disjunction : disjunctionType.getDisjunctions()) {
          if (!InheritanceUtil.isInheritor(disjunction, CommonClassNames.JAVA_LANG_THROWABLE)) {
            return false;
          }
        }
        return true;
      }
      return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE);
    }

    @Nullable
    public static String buildString(@Nullable PsiExpression expression) {
      if (expression == null) {
        return null;
      }
      final Object value = ExpressionUtils.computeConstantExpression(expression);
      if (value == null) {
        final StringBuilder builder = new StringBuilder();
        return buildString(expression, builder) ? builder.toString() : null;
      }
      return value instanceof String stringValue ? stringValue : null;
    }

    private static boolean buildString(PsiExpression source, StringBuilder builder) {
      Stack<PsiExpression> psiExpressionStack = new Stack<>();
      psiExpressionStack.add(source);
      int processedVariablesNumber = 0;
      while (!psiExpressionStack.isEmpty()) {
        PsiExpression expression = psiExpressionStack.pop();
        if (expression == null) {
          return false;
        }
        final PsiType type = expression.getType();
        if (expression instanceof final PsiParenthesizedExpression parenthesizedExpression) {
          psiExpressionStack.add(parenthesizedExpression.getExpression());
          continue;
        }
        if (expression instanceof final PsiPolyadicExpression polyadicExpression) {
          if (!TypeUtils.isJavaLangString(type) && !PsiType.CHAR.equals(type)) {
            continue;
          }
          PsiExpression[] operands = polyadicExpression.getOperands();
          for (int i = operands.length - 1; i >= 0; i--) {
            psiExpressionStack.add(operands[i]);
          }
          continue;
        }
        if (expression instanceof PsiLiteralExpression) {
          if (TypeUtils.isJavaLangString(type) || PsiType.CHAR.equals(type)) {
            final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
            builder.append(literalExpression.getValue());
          }
          continue;
        }
        //allow to resolve local variables only several times, not to process too much
        if (MAX_PROCESSED_VARIABLES > processedVariablesNumber && expression instanceof PsiReferenceExpression psiReferenceExpression &&
            psiReferenceExpression.resolve() instanceof PsiLocalVariable psiLocalVariable) {
          PsiCodeBlock block = PsiTreeUtil.getParentOfType(psiLocalVariable, PsiCodeBlock.class);
          if (block == null) return false;
          if (VariableAccessUtils.variableIsAssigned(psiLocalVariable, block)) return false;
          processedVariablesNumber++;
          psiExpressionStack.add(psiLocalVariable.getInitializer());
          continue;
        }
        if (!TypeUtils.isJavaLangString(type) /*&& !PsiType.CHAR.equals(type)*/) {
          // no one is crazy enough to add placeholders via char variables right?
          continue;
        }
        final Object value = ExpressionUtils.computeConstantExpression(expression);
        if (value == null) {
          return false;
        }
        builder.append(value);
      }
      return true;
    }

    private static int countPlaceholders(String string) {
      int count = 0;
      final int length = string.length();
      boolean escaped = false;
      boolean placeholder = false;
      for (int i = 0; i < length; i++) {
        final char c = string.charAt(i);
        if (c == '\\') {
          escaped = !escaped;
        }
        else if (c == '{') {
          if (!escaped) placeholder = true;
        }
        else if (c == '}') {
          if (placeholder) {
            count++;
            placeholder = false;
          }
        }
        else {
          escaped = false;
          placeholder = false;
        }
      }
      return count;
    }
  }

  private enum ResultType {
    PLACE_HOLDER_MISMATCH, INCORRECT_STRING, SUCCESS
  }

  private enum LoggerType {
    SLF4J, EQUAL_PLACEHOLDERS, LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE
  }

  private interface LoggerTypeSearcher {
    LoggerType findType(PsiMethodCallExpression expression, LoggerContext context);
  }

  private record LoggerContext(boolean ignoreSlf4jThrowableHavePlaceholder) {
  }

  private record Result(int argumentCount, int placeholderCount, ResultType result) {
  }
}