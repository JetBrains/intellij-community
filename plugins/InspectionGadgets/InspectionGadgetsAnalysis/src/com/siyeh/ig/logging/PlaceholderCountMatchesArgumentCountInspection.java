// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.containers.Stack;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.bugs.FormatDecode;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.*;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

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
      if (context.log4jAsImplementationForSlf4j) {
        //use old style as more common
        return LoggerType.LOG4J_OLD_STYLE;
      }
      return LoggerType.SLF4J;
    }
  };

  private static final LoggerTypeSearcher SLF4J_HOLDER_BUILDER = new LoggerTypeSearcher() {

    @Override
    public LoggerType findType(PsiMethodCallExpression expression, LoggerContext context) {
      if (context.log4jAsImplementationForSlf4j) {
        return LoggerType.EQUAL_PLACEHOLDERS;
      }
      PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
      if (SLF4J_BUILDER.matches(qualifierExpression)) {
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
  private static final int MAX_PARTS = 20;
  public static final String LOG_4_J_LOGGER = "org.apache.logging.slf4j.Log4jLogger";

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Result result = (Result)infos[0];
    if (result.result == ResultType.INCORRECT_STRING) {
      return InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.incorrect.problem.descriptor");
    }
    if (result.result == ResultType.PARTIAL_PLACE_HOLDER_MISMATCH) {
      return InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.fewer.problem.partial.descriptor",
                                             result.argumentCount(), result.placeholderCount());
    }
    return (result.argumentCount() > result.placeholderCount())
           ? InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.more.problem.descriptor",
                                             result.argumentCount(), result.placeholderCount())
           : InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.fewer.problem.descriptor",
                                             result.argumentCount(), result.placeholderCount());
  }

  @SuppressWarnings("PublicField")
  public Slf4jToLog4J2Type slf4jToLog4J2Type = Slf4jToLog4J2Type.AUTO;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      dropdown("slf4jToLog4J2Type", InspectionGadgetsBundle.message(
                 "placeholder.count.matches.argument.count.slf4j.throwable.option"),
               option(Slf4jToLog4J2Type.AUTO,
                      InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.slf4j.throwable.option.auto")),
               option(Slf4jToLog4J2Type.NO,
                      InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.slf4j.throwable.option.no")),
               option(Slf4jToLog4J2Type.YES,
                      InspectionGadgetsBundle.message("placeholder.count.matches.argument.count.slf4j.throwable.option.yes"))));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PlaceholderCountMatchesArgumentCountVisitor();
  }

  private class PlaceholderCountMatchesArgumentCountVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);

      LoggerTypeSearcher holder = LOGGER_TYPE_SEARCHERS.mapFirst(expression);
      if (holder == null) return;
      boolean log4jAsImplementationForSlf4j = switch (slf4jToLog4J2Type) {
        case AUTO -> hasBridgeFromSlf4jToLog4j2(expression);
        case YES -> true;
        case NO -> false;
      };
      LoggerType loggerType = holder.findType(expression, new LoggerContext(log4jAsImplementationForSlf4j));
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
      boolean lastArgumentIsSupplier =
        couldBeThrowableSupplier(loggerType, parameters[parameters.length - 1], arguments[arguments.length - 1]);
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
              lastArgumentIsSupplier = initializers.length > 0 &&
                                       couldBeThrowableSupplier(loggerType, parameters[parameters.length - 1],
                                                                initializers[initializers.length - 1]);
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
      List<PartHolder> parts = collectParts(logStringArgument);
      if (parts == null) return;

      PlaceholderCountResult placeholderCountHolder = solvePlaceholderCount(loggerType, argumentCount, parts);
      if (placeholderCountHolder.status == PlaceholdersStatus.EMPTY) {
        return;
      }
      if (placeholderCountHolder.status == PlaceholdersStatus.ERROR_TO_PARSE_STRING) {
        registerError(logStringArgument, new Result(argumentCount, 0, ResultType.INCORRECT_STRING));
        return;
      }

      ResultType resultType = switch (loggerType) {
        case SLF4J -> {
          //according to the reference, an exception should not have a placeholder
          argumentCount = lastArgumentIsException ? argumentCount - 1 : argumentCount;
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            yield (placeholderCountHolder.count <= argumentCount) ? ResultType.SUCCESS : ResultType.PARTIAL_PLACE_HOLDER_MISMATCH;
          }
          yield (placeholderCountHolder.count == argumentCount) ? ResultType.SUCCESS : ResultType.PLACE_HOLDER_MISMATCH;
        }
        case EQUAL_PLACEHOLDERS -> {
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            yield placeholderCountHolder.count <= argumentCount ? ResultType.SUCCESS : ResultType.PARTIAL_PLACE_HOLDER_MISMATCH;
          }
          yield placeholderCountHolder.count == argumentCount ? ResultType.SUCCESS : ResultType.PLACE_HOLDER_MISMATCH;
        }
        case LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE -> {
          // if there is more than one argument and the last argument is an exception, but there is a placeholder for
          // the exception, then the stack trace won't be logged.
          ResultType type;
          if (placeholderCountHolder.status == PlaceholdersStatus.PARTIAL) {
            type =
              ((placeholderCountHolder.count <= argumentCount && (!lastArgumentIsException || argumentCount > 1)) ||
               (lastArgumentIsException && placeholderCountHolder.count <= argumentCount - 1) ||
               //consider the most general case
               (lastArgumentIsSupplier && (placeholderCountHolder.count <= argumentCount))) ?
              ResultType.SUCCESS : ResultType.PARTIAL_PLACE_HOLDER_MISMATCH;
          }
          else {
            type =
              ((placeholderCountHolder.count == argumentCount && (!lastArgumentIsException || argumentCount > 1)) ||
               (lastArgumentIsException && placeholderCountHolder.count == argumentCount - 1) ||
               //consider the most general case
               (lastArgumentIsSupplier &&
                (placeholderCountHolder.count == argumentCount || placeholderCountHolder.count == argumentCount - 1))) ?
              ResultType.SUCCESS : ResultType.PLACE_HOLDER_MISMATCH;
          }
          argumentCount = lastArgumentIsException ? argumentCount - 1 : argumentCount;
          yield type;
        }
      };

      if (resultType == ResultType.SUCCESS) {
        return;
      }

      registerError(logStringArgument, new Result(argumentCount, placeholderCountHolder.count, resultType));
    }

    private static boolean hasBridgeFromSlf4jToLog4j2(PsiElement element) {
      PsiFile file = element.getContainingFile();
      return CachedValuesManager.<Boolean>getCachedValue(file, () -> {
        var project = file.getProject();
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(LOG_4_J_LOGGER, file.getResolveScope());
        return new CachedValueProvider.Result<>(aClass != null, ProjectRootManager.getInstance(project));
      });
    }

    @NotNull
    private static PlaceholderCountResult solvePlaceholderCount(LoggerType loggerType, int argumentCount, List<PartHolder> holders) {
      if (loggerType == LoggerType.LOG4J_FORMATTED_STYLE) {
        StringBuilder prefix = new StringBuilder();
        boolean full = true;
        for (PartHolder holder : holders) {
          if (holder.isConstant && holder.text != null) {
            prefix.append(holder.text);
          }
          else {
            full = false;
            break;
          }
        }
        if (prefix.isEmpty()) {
          return new PlaceholderCountResult(0, PlaceholdersStatus.EMPTY);
        }
        FormatDecode.Validator[] validators;
        try {
          if (full) {
            validators = FormatDecode.decode(prefix.toString(), argumentCount);
          }
          else {
            validators = FormatDecode.decodePrefix(prefix.toString(), argumentCount);
          }
        }
        catch (FormatDecode.IllegalFormatException e) {
          return new PlaceholderCountResult(0, PlaceholdersStatus.ERROR_TO_PARSE_STRING);
        }
        return new PlaceholderCountResult(validators.length, full ? PlaceholdersStatus.EXACTLY : PlaceholdersStatus.PARTIAL);
      }
      else {
        return countPlaceholders(holders);
      }
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

    private static boolean couldBeThrowableSupplier(@NotNull LoggerType loggerType,
                                                    @Nullable PsiParameter lastParameter,
                                                    @Nullable PsiExpression lastArgument) {
      if (loggerType != LoggerType.LOG4J_OLD_STYLE && loggerType != LoggerType.LOG4J_FORMATTED_STYLE) {
        return false;
      }
      if (lastParameter == null || lastArgument == null) {
        return false;
      }
      PsiType lastParameterType = lastParameter.getType();
      if (lastParameterType instanceof PsiEllipsisType psiEllipsisType) {
        lastParameterType = psiEllipsisType.getComponentType();
      }
      if (!(InheritanceUtil.isInheritor(lastParameterType, CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER) ||
            InheritanceUtil.isInheritor(lastParameterType, "org.apache.logging.log4j.util.Supplier"))) {
        return false;
      }
      PsiClassType throwable = PsiType.getJavaLangThrowable(lastArgument.getManager(), lastArgument.getResolveScope());

      if (lastArgument instanceof PsiLambdaExpression lambdaExpression) {
        for (PsiExpression expression : LambdaUtil.getReturnExpressions(lambdaExpression)) {
          if (expression == null || expression.getType() == null || !throwable.isConvertibleFrom(expression.getType())) {
            return false;
          }
        }
        return true;
      }
      if (lastArgument instanceof PsiMethodReferenceExpression referenceExpression) {
        PsiType psiType = PsiMethodReferenceUtil.getMethodReferenceReturnType(referenceExpression);
        if (psiType == null) return false;
        return throwable.isConvertibleFrom(psiType);
      }

      PsiType type = lastArgument.getType();
      if (type == null) return false;
      PsiType functionalReturnType = LambdaUtil.getFunctionalInterfaceReturnType(type);
      if (functionalReturnType == null) return false;
      return throwable.isConvertibleFrom(functionalReturnType);
    }

    @Nullable
    private static List<PartHolder> collectParts(@Nullable PsiExpression expression) {
      if (expression == null) {
        return null;
      }
      if (expression.getType() == null || !expression.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return null;
      }
      CommonDataflow.DataflowResult dataflowResult = CommonDataflow.getDataflowResult(expression);
      if (dataflowResult == null) return null;

      List<PartHolder> parts = new ArrayList<>();
      Stack<PsiExpression> concatenationStack = new Stack<>();
      concatenationStack.add(expression);
      while (!concatenationStack.isEmpty()) {
        PsiExpression currentExpression = concatenationStack.pop();
        //limit parts not to process too long
        if (parts.size() > MAX_PARTS) {
          parts.add(new PartHolder(null, false));
          return parts;
        }
        if (currentExpression == null) {
          //got something strange
          parts.add(new PartHolder(null, false));
          continue;
        }
        final PsiType type = currentExpression.getType();
        if (!TypeUtils.isJavaLangString(type) && !PsiTypes.charType().equals(type)) {
          parts.add(new PartHolder(null, true));
          continue;
        }

        final Set<Object> values = dataflowResult.getExpressionValues(currentExpression);
        if (values.size() == 1) {
          Object next = values.iterator().next();
          parts.add(new PartHolder(next != null ? next.toString() : null, true));
          continue;
        }
        else if (values.size() > 1) {
          parts.add(new PartHolder(null, false));
          continue;
        }

        if (currentExpression instanceof PsiLiteralExpression literalExpression) {
          Object value = literalExpression.getValue();
          parts.add(new PartHolder(value == null ? null : value.toString(), true));
          continue;
        }

        if (currentExpression instanceof final PsiParenthesizedExpression parenthesizedExpression) {
          concatenationStack.add(parenthesizedExpression.getExpression());
          continue;
        }

        if (currentExpression instanceof final PsiPolyadicExpression polyadicExpression) {
          PsiExpression[] operands = polyadicExpression.getOperands();
          for (int i = operands.length - 1; i >= 0; i--) {
            concatenationStack.add(operands[i]);
          }
          continue;
        }

        if (currentExpression instanceof PsiReferenceExpression psiReferenceExpression &&
            psiReferenceExpression.resolve() instanceof PsiVariable variable) {

          if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            concatenationStack.add(variable.getInitializer());
            continue;
          }
          if (!(variable instanceof PsiLocalVariable psiLocalVariable)) {
            parts.add(new PartHolder(null, false));
            continue;
          }
          PsiElement parent = PsiTreeUtil.findCommonParent(psiLocalVariable, expression);
          if (parent == null) {
            parts.add(new PartHolder(null, false));
            continue;
          }
          if (VariableAccessUtils.variableIsAssignedBeforeReference(psiReferenceExpression, parent)) {
            parts.add(new PartHolder(null, false));
            continue;
          }
          concatenationStack.add(psiLocalVariable.getInitializer());
          continue;
        }
        parts.add(new PartHolder(null, false));
      }

      for (PartHolder part : parts) {
        if (part.isConstant && part.text != null) {
          return parts;
        }
      }
      return null;
    }
  }

  private static PlaceholderCountResult countPlaceholders(List<PartHolder> holders) {
    int count = 0;
    boolean full = true;
    for (int holderIndex = 0; holderIndex < holders.size(); holderIndex++) {
      PartHolder partHolder = holders.get(holderIndex);
      if (!partHolder.isConstant) {
        full = false;
        continue;
      }
      String string = partHolder.text;
      if (string == null) {
        continue;
      }
      final int length = string.length();
      boolean escaped = false;
      boolean placeholder = false;
      for (int i = 0; i < length; i++) {
        final char c = string.charAt(i);
        if (c == '\\') {
          escaped = !escaped;
        }
        else if (c == '{') {
          if (holderIndex != 0 && i == 0 && !holders.get(holderIndex - 1).isConstant) {
            continue;
          }
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
    }
    return new PlaceholderCountResult(count, full ? PlaceholdersStatus.EXACTLY : PlaceholdersStatus.PARTIAL);
  }

  private enum ResultType {
    PARTIAL_PLACE_HOLDER_MISMATCH, PLACE_HOLDER_MISMATCH, INCORRECT_STRING, SUCCESS
  }

  private enum LoggerType {
    SLF4J, EQUAL_PLACEHOLDERS, LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE
  }

  private interface LoggerTypeSearcher {
    LoggerType findType(PsiMethodCallExpression expression, LoggerContext context);
  }

  private record LoggerContext(boolean log4jAsImplementationForSlf4j) {
  }

  private record Result(int argumentCount, int placeholderCount, ResultType result) {
  }

  private record PlaceholderCountResult(int count, PlaceholdersStatus status) {
  }

  private enum PlaceholdersStatus {
    EXACTLY, PARTIAL, ERROR_TO_PARSE_STRING, EMPTY
  }

  /**
   * @param text       - null if it is a literal, which is not String or Character
   * @param isConstant - it is a constant
   */
  private record PartHolder(@Nullable String text, boolean isConstant) {
  }

  enum Slf4jToLog4J2Type {
    AUTO, YES, NO
  }
}