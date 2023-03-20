// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

public final class ExpressionUtils {
  private static final @NonNls Set<String> IMPLICIT_TO_STRING_METHOD_NAMES =
    Set.of("append", "format", "print", "printf", "println", "valueOf");
  @NonNls private static final Set<String> convertableBoxedClassNames = new HashSet<>(3);

  static {
    convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_BYTE);
    convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_CHARACTER);
    convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_SHORT);
  }

  private static final CallMatcher KNOWN_SIMPLE_CALLS =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptyList", "emptySet", "emptyIterator", "emptyMap", "emptySortedMap",
                           "emptySortedSet", "emptyListIterator").parameterCount(0);

  private static final CallMatcher GET_OR_DEFAULT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "getOrDefault").parameterCount(2);

  private ExpressionUtils() {}

  @Nullable
  public static Object computeConstantExpression(@Nullable PsiExpression expression) {
    return computeConstantExpression(expression, false);
  }

  @Nullable
  public static Object computeConstantExpression(@Nullable PsiExpression expression, boolean throwConstantEvaluationOverflowException) {
    if (expression == null) {
      return null;
    }
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiConstantEvaluationHelper constantEvaluationHelper = psiFacade.getConstantEvaluationHelper();
    return constantEvaluationHelper.computeConstantExpression(expression, throwConstantEvaluationOverflowException);
  }

  public static boolean isConstant(PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    if (CollectionUtils.isEmptyArray(field)) {
      return true;
    }
    final PsiType type = field.getType();
    return ClassUtils.isImmutable(type);
  }

  public static boolean hasExpressionCount(@Nullable PsiExpressionList expressionList, int count) {
    return ControlFlowUtils.hasChildrenOfTypeCount(expressionList, count, PsiExpression.class);
  }

  @Nullable
  public static PsiExpression getFirstExpressionInList(@Nullable PsiExpressionList expressionList) {
    return PsiTreeUtil.getChildOfType(expressionList, PsiExpression.class);
  }

  @Nullable
  public static PsiExpression getOnlyExpressionInList(@Nullable PsiExpressionList expressionList) {
    return ControlFlowUtils.getOnlyChildOfType(expressionList, PsiExpression.class);
  }

  public static boolean isDeclaredConstant(PsiExpression expression) {
    PsiField field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
    if (field == null) {
      final PsiAssignmentExpression assignmentExpression =
        PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class);
      if (assignmentExpression == null) {
        return false;
      }
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof final PsiReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return false;
      }
      field = (PsiField)target;
    }
    return field.hasModifierProperty(PsiModifier.STATIC) &&
           field.hasModifierProperty(PsiModifier.FINAL);
  }

  @Contract("null -> false")
  public static boolean isEvaluatedAtCompileTime(@Nullable PsiExpression expression) {
    if (expression instanceof PsiLiteralExpression) {
      return true;
    }
    if (expression instanceof final PsiPolyadicExpression polyadicExpression) {
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (!isEvaluatedAtCompileTime(operand)) {
          return false;
        }
      }
      return true;
    }
    if (expression instanceof final PsiPrefixExpression prefixExpression) {
      final PsiExpression operand = prefixExpression.getOperand();
      return isEvaluatedAtCompileTime(operand);
    }
    if (expression instanceof final PsiReferenceExpression referenceExpression) {
      final PsiElement qualifier = referenceExpression.getQualifier();
      if (qualifier instanceof PsiThisExpression) {
        return false;
      }
      final PsiElement element = referenceExpression.resolve();
      if (element instanceof final PsiVariable variable) {
        if (PsiTreeUtil.isAncestor(variable, expression, true)) {
          return false;
        }
        return variable.hasModifierProperty(PsiModifier.FINAL) && isEvaluatedAtCompileTime(PsiFieldImpl.getDetachedInitializer(variable));
      }
    }
    if (expression instanceof final PsiParenthesizedExpression parenthesizedExpression) {
      final PsiExpression deparenthesizedExpression = parenthesizedExpression.getExpression();
      return isEvaluatedAtCompileTime(deparenthesizedExpression);
    }
    if (expression instanceof final PsiConditionalExpression conditionalExpression) {
      final PsiExpression condition = conditionalExpression.getCondition();
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      return isEvaluatedAtCompileTime(condition) &&
             isEvaluatedAtCompileTime(thenExpression) &&
             isEvaluatedAtCompileTime(elseExpression);
    }
    if (expression instanceof final PsiTypeCastExpression typeCastExpression) {
      final PsiTypeElement castType = typeCastExpression.getCastType();
      if (castType == null) {
        return false;
      }
      final PsiType type = castType.getType();
      return TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, type) &&
             isEvaluatedAtCompileTime(typeCastExpression.getOperand());
    }
    return false;
  }

  @Nullable
  public static PsiLiteralExpression getLiteral(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiLiteralExpression literal) return literal;
    if (!(expression instanceof PsiTypeCastExpression typeCastExpression)) return null;
    final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(typeCastExpression.getOperand());
    return operand instanceof PsiLiteralExpression literal ? literal : null;
  }

  public static boolean isLiteral(@Nullable PsiExpression expression) {
    return getLiteral(expression) != null;
  }

  public static boolean isEmptyStringLiteral(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    final String text = expression.getText();
    return "\"\"".equals(text);
  }

  @Contract("null -> false")
  public static boolean isNullLiteral(@Nullable PsiCaseLabelElement element) {
    if (!(element instanceof PsiExpression expression)) return false;
    expression = PsiUtil.deparenthesizeExpression(expression);
    return expression instanceof PsiLiteralExpression && ((PsiLiteralExpression)expression).getValue() == null;
  }

  /**
   * Returns stream of sub-expressions of supplied expression which could be equal (by ==) to resulting
   * value of the expression. The expression value is guaranteed to be equal to one of returned sub-expressions.
   *
   * <p>
   * E.g. for {@code ((a) ? (Foo)b : (c))} the stream will contain b and c.
   * </p>
   *
   * @param expression expression to create a stream from
   * @return a new stream
   */
  public static Stream<PsiExpression> nonStructuralChildren(@NotNull PsiExpression expression) {
    return StreamEx.ofTree(expression, e -> {
        if (e instanceof PsiConditionalExpression ternary) {
          return StreamEx.of(ternary.getThenExpression(), ternary.getElseExpression()).nonNull();
        }
        if (e instanceof PsiParenthesizedExpression parens) {
          return StreamEx.ofNullable(parens.getExpression());
        }
        if (e instanceof PsiSwitchExpression switchExpression) {
          PsiCodeBlock switchBody = switchExpression.getBody();
          if (switchBody == null) return StreamEx.empty();
          List<PsiExpression> result = new ArrayList<>();
          PsiStatement[] statements = switchBody.getStatements();
          for (PsiStatement statement : statements) {
            if (statement instanceof PsiSwitchLabeledRuleStatement rule) {
              PsiStatement ruleBody = rule.getBody();
              if (ruleBody instanceof PsiBlockStatement blockStatement) {
                collectYieldExpressions(blockStatement, switchExpression, result);
              }
              else if (ruleBody instanceof PsiExpressionStatement expr) {
                result.add(expr.getExpression());
              }
            }
            else {
              collectYieldExpressions(statement, switchExpression, result);
            }
          }
          return StreamEx.of(result);
        }
        return null;
      }).remove(e -> e instanceof PsiConditionalExpression ||
                     e instanceof PsiParenthesizedExpression ||
                     e instanceof PsiSwitchExpression)
      .map(e -> {
        if (e instanceof PsiTypeCastExpression cast) {
          PsiExpression operand = cast.getOperand();
          if (operand != null && !(e.getType() instanceof PsiPrimitiveType) &&
              (!(operand.getType() instanceof PsiPrimitiveType) || PsiTypes.nullType().equals(operand.getType()))) {
            // Ignore to-primitive/from-primitive casts as they may actually change the value
            return PsiUtil.skipParenthesizedExprDown(operand);
          }
        }
        return e;
      })
      .nonNull()
      .flatMap(e -> {
        if (e instanceof PsiMethodCallExpression call && GET_OR_DEFAULT.test(call)) {
          return StreamEx.of(e, call.getArgumentList().getExpressions()[1]);
        }
        return StreamEx.of(e);
      });
  }

  private static void collectYieldExpressions(@NotNull PsiStatement statement,
                                              @NotNull PsiSwitchExpression switchExpression,
                                              @NotNull Collection<PsiExpression> result) {
    Collection<PsiYieldStatement> yields = statement instanceof PsiYieldStatement yieldStatement
                                           ? Collections.singleton(yieldStatement)
                                           : PsiTreeUtil.findChildrenOfType(statement, PsiYieldStatement.class);
    List<PsiYieldStatement> myYields = ContainerUtil.filter(yields, st -> st.findEnclosingExpression() == switchExpression);
    for (PsiYieldStatement yield : myYields) {
      ContainerUtil.addIfNotNull(result, yield.getExpression());
    }
  }

  public static boolean isZero(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final Object value = computeConstantExpression(expression);
    if (value == null) {
      return false;
    }
    return value instanceof Double dValue && dValue == 0.0 ||
           value instanceof Float fValue && fValue == 0.0f ||
           value instanceof Integer iValue && iValue == 0 ||
           value instanceof Long lValue && lValue == 0L ||
           value instanceof Short sValue && sValue == 0 ||
           value instanceof Character cValue && cValue == 0 ||
           value instanceof Byte bValue && bValue == 0;
  }

  public static boolean isOne(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final Object value = computeConstantExpression(expression);
    if (value == null) {
      return false;
    }
    //noinspection FloatingPointEquality
    return value instanceof Double dValue && dValue == 1.0 ||
           value instanceof Float fValue && fValue == 1.0f ||
           value instanceof Integer iValue && iValue == 1 ||
           value instanceof Long lValue && lValue == 1L ||
           value instanceof Short sValue && sValue == 1 ||
           value instanceof Character cValue && cValue == 1 ||
           value instanceof Byte bValue && bValue == 1;
  }

  public static boolean isNegation(@Nullable PsiExpression condition,
                                   boolean ignoreNegatedNullComparison, boolean ignoreNegatedZeroComparison) {
    condition = PsiUtil.skipParenthesizedExprDown(condition);
    if (condition instanceof final PsiPrefixExpression prefixExpression) {
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      return tokenType.equals(JavaTokenType.EXCL);
    }
    else if (condition instanceof final PsiBinaryExpression binaryExpression) {
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
      if (lhs == null || rhs == null) {
        return false;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.NE)) {
        if (ignoreNegatedNullComparison) {
          final String lhsText = lhs.getText();
          final String rhsText = rhs.getText();
          if (PsiKeyword.NULL.equals(lhsText) || PsiKeyword.NULL.equals(rhsText)) {
            return false;
          }
        }
        return !(ignoreNegatedZeroComparison && (isZeroLiteral(lhs) || isZeroLiteral(rhs)));
      }
    }
    return false;
  }

  private static boolean isZeroLiteral(PsiExpression expression) {
    if (!(expression instanceof PsiLiteralExpression literal)) return false;
    final Object value = literal.getValue();
    return value instanceof Integer iValue && iValue == 0 ||
           value instanceof Long lValue && lValue == 0L;
  }

  public static boolean isOffsetArrayAccess(@Nullable PsiExpression expression, @NotNull PsiVariable variable) {
    final PsiExpression strippedExpression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(strippedExpression instanceof PsiArrayAccessExpression arrayAccessExpression)) {
      return false;
    }
    final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    if (VariableAccessUtils.variableIsUsed(variable, arrayExpression)) {
      return false;
    }
    final PsiExpression index = arrayAccessExpression.getIndexExpression();
    if (index == null) {
      return false;
    }
    return expressionIsOffsetVariableLookup(index, variable);
  }

  private static boolean expressionIsOffsetVariableLookup(
    @Nullable PsiExpression expression, @NotNull PsiVariable variable) {
    if (isReferenceTo(expression, variable)) {
      return true;
    }
    if (!(PsiUtil.skipParenthesizedExprDown(expression) instanceof PsiBinaryExpression binaryExpression)) {
      return false;
    }
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (!JavaTokenType.PLUS.equals(tokenType) &&
        !JavaTokenType.MINUS.equals(tokenType)) {
      return false;
    }
    final PsiExpression lhs = binaryExpression.getLOperand();
    if (expressionIsOffsetVariableLookup(lhs, variable)) {
      return true;
    }
    final PsiExpression rhs = binaryExpression.getROperand();
    return expressionIsOffsetVariableLookup(rhs, variable) &&
           !JavaTokenType.MINUS.equals(tokenType);
  }

  public static boolean isVariableLessThanComparison(@Nullable PsiExpression expression, @NotNull PsiVariable variable) {
    PsiBinaryExpression binaryExpression = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiBinaryExpression.class);
    if (binaryExpression == null) return false;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.LT) || tokenType.equals(JavaTokenType.LE)) {
      return isReferenceTo(binaryExpression.getLOperand(), variable);
    }
    else if (tokenType.equals(JavaTokenType.GT) || tokenType.equals(JavaTokenType.GE)) {
      return isReferenceTo(binaryExpression.getROperand(), variable);
    }
    return false;
  }

  /**
   * Returns true if given expression is an operand of String concatenation.
   * Also works if expression parent is {@link PsiParenthesizedExpression}.
   *
   * @param expression expression to check
   * @return true if given expression is an operand of String concatenation
   */
  public static boolean isStringConcatenationOperand(PsiExpression expression) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (!(parent instanceof PsiPolyadicExpression polyadicExpression)) {
      return false;
    }
    if (!JavaTokenType.PLUS.equals(polyadicExpression.getOperationTokenType())) {
      return false;
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operands.length < 2) {
      return false;
    }
    for (int i = 0; i < operands.length; i++) {
      final PsiExpression operand = operands[i];
      if (PsiUtil.skipParenthesizedExprDown(operand) == expression) {
        return i == 0 && TypeUtils.isJavaLangString(operands[1].getType());
      }
      final PsiType type = operand.getType();
      if (TypeUtils.isJavaLangString(type)) {
        return true;
      }
    }
    return false;
  }


  public static boolean hasType(@Nullable PsiExpression expression, @NonNls @NotNull String typeName) {
    if (expression == null) {
      return false;
    }
    if (typeName.equals(CommonClassNames.JAVA_LANG_STRING)) {
      if (expression instanceof PsiUnaryExpression || expression instanceof PsiInstanceOfExpression ||
          expression instanceof PsiFunctionalExpression) {
        return false;
      }
      if (expression instanceof PsiPolyadicExpression poly) {
        IElementType tokenType = poly.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.PLUS)) return false;
      }
    }
    final PsiType type = expression.getType();
    return TypeUtils.typeEquals(typeName, type);
  }

  public static boolean hasStringType(@Nullable PsiExpression expression) {
    return hasType(expression, CommonClassNames.JAVA_LANG_STRING);
  }

  /**
   * The method checks if the passed expression needs to be converted to string explicitly,
   * because the containing expression (e.g. a {@code PrintStream#println} call or string concatenation expression)
   * will convert to the string automatically.
   * <p>
   * This is the case for some StringBuilder/Buffer, PrintStream/Writer and some logging methods.
   * Otherwise, it considers the conversion necessary and returns true.
   *
   * @param expression an expression to examine
   * @param throwable  is the first parameter a conversion to string on a throwable? Either {@link Throwable#toString()}
   *                   or {@link String#valueOf(Object)}
   * @return true if the explicit conversion to string is required, otherwise - false
   */
  public static boolean isConversionToStringNecessary(PsiExpression expression, boolean throwable) {
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
    if (parent instanceof PsiPolyadicExpression polyadicExpression) {
      final PsiType type = polyadicExpression.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, type)) {
        return true;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      boolean expressionSeen = false;
      for (int i = 0, length = operands.length; i < length; i++) {
        final PsiExpression operand = operands[i];
        if (PsiTreeUtil.isAncestor(operand, expression, false)) {
          if (i > 0) return true;
          expressionSeen = true;
        }
        else if ((!expressionSeen || i == 1) && TypeUtils.isJavaLangString(operand.getType())) {
          return false;
        }
      }
      return true;
    }
    else if (parent instanceof PsiExpressionList expressionList) {
      final PsiElement grandParent = expressionList.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return true;
      }
      final PsiReferenceExpression methodExpression1 = methodCallExpression.getMethodExpression();
      @NonNls final String name = methodExpression1.getReferenceName();
      final PsiExpression[] expressions = expressionList.getExpressions();
      if ("insert".equals(name)) {
        if (expressions.length < 2 || !expression.equals(PsiUtil.skipParenthesizedExprDown(expressions[1]))) {
          return true;
        }
        return !isCallToMethodIn(methodCallExpression, "java.lang.StringBuilder", "java.lang.StringBuffer");
      } else if ("append".equals(name)) {
        if (expressions.length != 1 || !expression.equals(PsiUtil.skipParenthesizedExprDown(expressions[0]))) {
          return true;
        }
        return !isCallToMethodIn(methodCallExpression, "java.lang.StringBuilder", "java.lang.StringBuffer");
      } else if ("print".equals(name) || "println".equals(name)) {
        return !isCallToMethodIn(methodCallExpression, "java.io.PrintStream", "java.io.PrintWriter");
      } else if ("trace".equals(name) || "debug".equals(name) || "info".equals(name) || "warn".equals(name) || "error".equals(name)) {
        if (!isCallToMethodIn(methodCallExpression, "org.slf4j.Logger")) {
          return true;
        }
        int l = 1;
        for (int i = 0; i < expressions.length; i++) {
          final PsiExpression expression1 = expressions[i];
          if (i == 0 && TypeUtils.expressionHasTypeOrSubtype(expression1, "org.slf4j.Marker")) {
            l = 2;
          }
          if (expression1 == expression) {
            if (i < l || (throwable && i == expressions.length - 1)) {
              return true;
            }
          }
        }
      }
      else if (FormatUtils.isFormatCall(methodCallExpression)) {
        return isConversionToStringNecessary(expression, methodCallExpression);
      }
      else {
        return true;
      }
    }
    else {
      return true;
    }
    return false;
  }

  /**
   * The method checks if the passed expression is an argument of {@code formatCall} which
   * needs to be converted to string explicitly.
   *
   * @param expression an expression to examine
   * @param formatCall e.g. a {@code java.io.Console#format} call
   * @return true if the explicit conversion to string is required, otherwise - false
   */
  private static boolean isConversionToStringNecessary(PsiExpression expression,
                                                       PsiMethodCallExpression formatCall) {
    PsiExpressionList expressionList = formatCall.getArgumentList();
    PsiExpression formatArgument = FormatUtils.getFormatArgument(expressionList);
    if (PsiTreeUtil.isAncestor(formatArgument, expression, false)) return true;
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    PsiExpression[] expressions = formatCall.getArgumentList().getExpressions();
    int formatArgumentIndex = ArrayUtil.find(expressions, formatArgument);
    if (formatArgumentIndex == -1) return false;
    int expressionIndex = ArrayUtil.find(expressions, expression);
    if (expressionIndex != formatArgumentIndex + 1 || expressionIndex != expressions.length - 1) return false;
    PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall((PsiMethodCallExpression)expression);
    if (qualifierCall == null || qualifierCall.getTypeArguments().length > 0) return false;
    PsiExpression qualifier = qualifierCall.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return false;
    PsiClassType type = tryCast(qualifier.getType(), PsiClassType.class);
    if (type == null || type.isRaw()) return false;
    PsiMethod method = qualifierCall.resolveMethod();
    if (method == null) return false;
    PsiType returnType = method.getReturnType();
    PsiTypeParameter returnTypeParameter = tryCast(PsiUtil.resolveClassInClassTypeOnly(returnType), PsiTypeParameter.class);
    return returnTypeParameter != null &&
           returnTypeParameter.getOwner() == method &&
           !ContainerUtil.map(method.getParameterList().getParameters(), PsiParameter::getType).contains(returnType);
  }

  private static boolean isCallToMethodIn(PsiMethodCallExpression methodCallExpression, String... classNames) {
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    return ArrayUtil.contains(qualifiedName, classNames);
  }

  public static boolean isNegative(@NotNull PsiExpression expression) {
    return expression.getParent() instanceof PsiPrefixExpression prefixExpression &&
           JavaTokenType.MINUS.equals(prefixExpression.getOperationTokenType());
  }

  @Contract("null, _ -> null")
  @Nullable
  public static PsiVariable getVariableFromNullComparison(PsiExpression expression, boolean equals) {
    final PsiReferenceExpression referenceExpression = getReferenceExpressionFromNullComparison(expression, equals);
    final PsiElement target = referenceExpression != null ? referenceExpression.resolve() : null;
    return target instanceof PsiVariable ? (PsiVariable)target : null;
  }

  @Contract("null, _ -> null")
  @Nullable
  public static PsiReferenceExpression getReferenceExpressionFromNullComparison(PsiExpression expression, boolean equals) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
      return null;
    }
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (equals) {
      if (!JavaTokenType.EQEQ.equals(tokenType)) {
        return null;
      }
    }
    else {
      if (!JavaTokenType.NE.equals(tokenType)) {
        return null;
      }
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operands.length != 2) {
      return null;
    }
    PsiExpression comparedToNull = null;
    if (PsiTypes.nullType().equals(operands[0].getType())) {
      comparedToNull = operands[1];
    }
    else if (PsiTypes.nullType().equals(operands[1].getType())) {
      comparedToNull = operands[0];
    }
    comparedToNull = PsiUtil.skipParenthesizedExprDown(comparedToNull);

    return comparedToNull instanceof PsiReferenceExpression ? (PsiReferenceExpression)comparedToNull : null;
  }

  /**
   * Returns the expression compared with null if the supplied {@link PsiBinaryExpression} is null check (either with {@code ==}
   * or with {@code !=}). Returns null otherwise.
   *
   * @param binOp binary expression to extract the value compared with null from
   * @return value compared with null
   */
  @Nullable
  public static PsiExpression getValueComparedWithNull(@NotNull PsiBinaryExpression binOp) {
    final IElementType tokenType = binOp.getOperationTokenType();
    if(!tokenType.equals(JavaTokenType.EQEQ) && !tokenType.equals(JavaTokenType.NE)) return null;
    final PsiExpression left = binOp.getLOperand();
    final PsiExpression right = binOp.getROperand();
    if(isNullLiteral(right)) return left;
    if(isNullLiteral(left)) return right;
    return null;
  }


  /**
   * Returns the expression compared with zero if the supplied {@link PsiBinaryExpression} is zero check (with {@code ==}). Returns null otherwise.
   *
   * @param binOp binary expression to extract the value compared with zero from
   * @return value compared with zero
   */
  @Nullable
  public static PsiExpression getValueComparedWithZero(@NotNull PsiBinaryExpression binOp) {
    return getValueComparedWithZero(binOp, JavaTokenType.EQEQ);
  }

  @Nullable
  public static PsiExpression getValueComparedWithZero(@NotNull PsiBinaryExpression binOp, IElementType opType) {
    if (!binOp.getOperationTokenType().equals(opType)) return null;
    PsiExpression rOperand = binOp.getROperand();
    if (rOperand == null) return null;
    PsiExpression lOperand = binOp.getLOperand();
    if (isZero(lOperand)) return rOperand;
    if (isZero(rOperand)) return lOperand;
    return null;
  }

  public static boolean isStringConcatenation(PsiElement element) {
    if (!(element instanceof final PsiPolyadicExpression expression)) return false;
    final PsiType type = expression.getType();
    return type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  /**
   * Returns true if the expression can be moved to earlier point in program order without possible semantic change or
   * notable performance handicap. Examples of simple expressions are:
   * <ul>
   * <li>literal (number, char, string, class literal, true, false, null)</li>
   * <li>compile-time constant</li>
   * <li>this</li>
   * <li>variable/parameter read</li>
   * <li>final field read (either static or this-qualified)</li>
   * <li>some static method calls known to return final static field (like {@code Collections.emptyList()})</li>
   * </ul>
   *
   * @param expression an expression to test (must be valid expression)
   * @return true if the supplied expression is simple
   */
  @Contract("null -> false")
  public static boolean isSafelyRecomputableExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiLiteralExpression ||
        expression instanceof PsiThisExpression ||
        expression instanceof PsiClassObjectAccessExpression ||
        isEvaluatedAtCompileTime(expression)) {
      return true;
    }
    if (expression instanceof PsiConditionalExpression cond) {
      return isSafelyRecomputableExpression(cond.getCondition()) &&
             isSafelyRecomputableExpression(cond.getThenExpression()) &&
             isSafelyRecomputableExpression(cond.getElseExpression());
    }
    if (expression instanceof PsiReferenceExpression ref) {
      PsiElement target = ref.resolve();
      if (target instanceof PsiLocalVariable || target instanceof PsiParameter) return true;
      PsiExpression qualifier = ref.getQualifierExpression();
      if (target == null && qualifier == null) return true;
      if (target instanceof PsiField field) {
        if (!field.hasModifierProperty(PsiModifier.FINAL)) return false;
        return qualifier == null || qualifier instanceof PsiThisExpression || field.hasModifierProperty(PsiModifier.STATIC);
      }
    }
    if (expression instanceof PsiMethodCallExpression call) {
      return KNOWN_SIMPLE_CALLS.test(call);
    }
    return false;
  }

  /**
   * Returns assignment expression if supplied element is a statement which contains assignment expression,
   * or it's an assignment expression itself. Only simple assignments are returned (like a = b, not a+= b).
   *
   * @param element element to get assignment expression from
   * @return extracted assignment or null if assignment is not found or assignment is compound
   */
  @Contract("null -> null")
  @Nullable
  public static PsiAssignmentExpression getAssignment(PsiElement element) {
    if(element instanceof PsiExpressionStatement) {
      element = ((PsiExpressionStatement)element).getExpression();
    }
    if (element instanceof PsiExpression) {
      element = PsiUtil.skipParenthesizedExprDown((PsiExpression)element);
      if (element instanceof PsiAssignmentExpression assignment && assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        return assignment;
      }
    }
    return null;
  }

  /**
   * Returns an expression assigned to the target variable if supplied element is
   * either simple (non-compound) assignment expression or an expression statement containing assignment expression
   * and the corresponding assignment l-value is the reference to target variable.
   *
   * @param element element to get assignment expression from
   * @param target a variable to extract an assignment to
   * @return extracted assignment r-value or null if assignment is not found or assignment is compound, or it's an assignment
   * to the wrong variable
   */
  @Contract("null, _ -> null; _, null -> null")
  public static PsiExpression getAssignmentTo(PsiElement element, PsiVariable target) {
    PsiAssignmentExpression assignment = getAssignment(element);
    if(assignment != null && isReferenceTo(assignment.getLExpression(), target)) {
      return assignment.getRExpression();
    }
    return null;
  }

  @Contract("null, _ -> false")
  public static boolean isLiteral(PsiElement element, Object value) {
    return element instanceof PsiLiteralExpression && value.equals(((PsiLiteralExpression)element).getValue());
  }

  public static boolean isAutoBoxed(@NotNull PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiParenthesizedExpression) {
      return false;
    }
    if (parent instanceof PsiExpressionList) {
      final PsiElement grandParent = parent.getParent();
      if (grandParent instanceof final PsiMethodCallExpression call) {
        final PsiMethod method = call.resolveMethod();
        if (method != null &&
            AnnotationUtil.isAnnotated(method, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, 0)) {
          return false;
        }
      }
    }
    final PsiType expressionType = expression.getType();
    if (PsiPrimitiveType.getUnboxedType(expressionType) != null && parent instanceof PsiUnaryExpression) {
      final IElementType sign = ((PsiUnaryExpression)parent).getOperationTokenType();
      return sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS;
    }
    if (expressionType == null || expressionType.equals(PsiTypes.voidType()) || !TypeConversionUtil.isPrimitiveAndNotNull(expressionType)) {
      return false;
    }
    final PsiPrimitiveType primitiveType = (PsiPrimitiveType)expressionType;
    final PsiClassType boxedType = primitiveType.getBoxedType(expression);
    if (boxedType == null) {
      return false;
    }
    final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
    if (expectedType == null || ClassUtils.isPrimitive(expectedType)) {
      return false;
    }
    if (!expectedType.isAssignableFrom(boxedType)) {
      // JLS 5.2 Assignment Conversion
      // check if a narrowing primitive conversion is applicable
      if (!(expectedType instanceof PsiClassType classType) || !PsiUtil.isConstantExpression(expression)) {
        return false;
      }
      final String className = classType.getCanonicalText();
      if (!convertableBoxedClassNames.contains(className)) {
        return false;
      }
      return PsiTypes.byteType().equals(expressionType) || PsiTypes.charType().equals(expressionType) ||
             PsiTypes.shortType().equals(expressionType) || PsiTypes.intType().equals(expressionType);
    }
    return true;
  }

  /**
   * If any operand of supplied binary expression refers to the supplied variable, returns other operand;
   * otherwise returns null.
   *
   * @param binOp {@link PsiBinaryExpression} to extract the operand from
   * @param variable variable to check against
   * @return operand or null
   */
  @Contract("null, _ -> null; !null, null -> null")
  public static PsiExpression getOtherOperand(@Nullable PsiBinaryExpression binOp, @Nullable PsiVariable variable) {
    if(binOp == null || variable == null) return null;
    if(isReferenceTo(binOp.getLOperand(), variable)) return binOp.getROperand();
    if(isReferenceTo(binOp.getROperand(), variable)) return binOp.getLOperand();
    return null;
  }

  @Contract("null, _ -> false; _, null -> false")
  public static boolean isReferenceTo(PsiExpression expression, PsiVariable variable) {
    if (variable == null) return false;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiReferenceExpression ref)) return false;
    if ((variable instanceof PsiLocalVariable || variable instanceof PsiParameter) && ref.isQualified()) {
      // Optimization: references to locals and parameters are unqualified
      return false;
    }
    return ref.isReferenceTo(variable);
  }

  /**
   * Returns a method call expression for the supplied qualifier
   *
   * @param qualifier for method call
   * @return a method call expression or null if the supplied expression is not a method call qualifier
   */
  @Contract(value = "null -> null", pure = true)
  public static PsiMethodCallExpression getCallForQualifier(PsiExpression qualifier) {
    if (qualifier != null &&
        PsiUtil.skipParenthesizedExprUp(qualifier.getParent()) instanceof PsiReferenceExpression methodExpr &&
        PsiTreeUtil.isAncestor(methodExpr.getQualifierExpression(), qualifier, false) &&
        methodExpr.getParent() instanceof PsiMethodCallExpression call) {
      return call;
    }
    return null;
  }

  /**
   * Returns an array expression from array length retrieval expression
   *
   * @param expression expression to extract an array expression from
   * @return an array expression or null if supplied expression is not array length retrieval
   */
  @Nullable
  @Contract("null -> null")
  public static PsiExpression getArrayFromLengthExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof final PsiReferenceExpression reference)) return null;
    final String referenceName = reference.getReferenceName();
    if (!HardcodedMethodConstants.LENGTH.equals(referenceName)) return null;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier == null) return null;
    final PsiType type = qualifier.getType();
    if (type == null || type.getArrayDimensions() <= 0) return null;
    return qualifier;
  }

  /**
   * Returns an effective qualifier for a reference. If qualifier is not specified, then tries to construct it
   * e.g. creating a corresponding {@link PsiThisExpression}.
   *
   * @param ref a reference expression to get an effective qualifier for
   * @return a qualifier or created (non-physical) {@link PsiThisExpression}.
   * May return null if reference points to local or member of anonymous class referred from inner class
   * or if reference points to non-static member of class from static context
   */
  @Nullable
  public static PsiExpression getEffectiveQualifier(@NotNull PsiReferenceExpression ref) {
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null) return qualifier;
    PsiMember member = tryCast(ref.resolve(), PsiMember.class);
    if (member == null) {
      // Reference resolves to non-member: probably variable/parameter/etc.
      return null;
    }
    return getEffectiveQualifier(ref, member);
  }

  /**
   * Returns an effective qualifier for a reference that resolves to a member of a Java class. If qualifier is not
   * specified, then tries to construct it e.g. creating a corresponding {@link PsiThisExpression}.
   *
   * @param ref    a reference expression to get an effective qualifier for
   * @param member a member the reference is resolved to
   * @return a qualifier or created (non-physical) {@link PsiThisExpression}.
   * May return null if reference points to local or member of anonymous class referred from inner class
   * or if reference points to non-static member of class from static context
   */
  public static PsiExpression getEffectiveQualifier(@NotNull PsiReferenceExpression ref, @NotNull PsiMember member) {
    PsiMember containingMember = PsiTreeUtil.getParentOfType(ref, PsiMethod.class, PsiClassInitializer.class, PsiField.class);
    if (!member.hasModifierProperty(PsiModifier.STATIC) && isStaticMember(containingMember)) {
      return null;
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(ref.getProject());
    PsiClass memberClass = member.getContainingClass();
    if (memberClass != null) {
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        return factory.createReferenceExpression(memberClass);
      }
      PsiClass containingClass = ClassUtils.getContainingClass(ref);
      if (containingClass == null) {
        containingClass = PsiTreeUtil.getContextOfType(ref, PsiClass.class);
      }
      if (!InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
        if (!member.hasModifierProperty(PsiModifier.STATIC) && isStaticMember(containingClass)) return null;
        containingClass = ClassUtils.getContainingClass(containingClass);
        while (containingClass != null && !InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
          if (!member.hasModifierProperty(PsiModifier.STATIC) && isStaticMember(containingClass)) return null;
          containingClass = ClassUtils.getContainingClass(containingClass);
        }
        if (containingClass != null) {
          String thisQualifier = containingClass.getQualifiedName();
          if (thisQualifier == null) {
            if (PsiUtil.isLocalClass(containingClass)) {
              thisQualifier = containingClass.getName();
            }
            else {
              // Cannot qualify anonymous class
              return null;
            }
          }
          return factory.createExpressionFromText(thisQualifier + "." + PsiKeyword.THIS, ref);
        }
      }
    }
    return factory.createExpressionFromText(PsiKeyword.THIS, ref);
  }

  private static boolean isStaticMember(@Nullable PsiMember member) {
    return member != null && member.hasModifierProperty(PsiModifier.STATIC);
  }

  /**
   * Bind a reference element to a new name. The type arguments (if present) remain the same.
   * The qualifier remains the same unless the original unqualified reference resolves
   * to statically imported member. In this case the qualifier could be added.
   *
   * @param ref reference element to rename
   * @param newName new name
   */
  public static void bindReferenceTo(@NotNull PsiReferenceExpression ref, @NotNull String newName) {
    PsiElement nameElement = ref.getReferenceNameElement();
    if(nameElement == null) {
      throw new IllegalStateException("Name element is null: "+ref);
    }
    if(newName.equals(nameElement.getText())) return;
    PsiClass aClass = null;
    if(ref.getQualifierExpression() == null) {
      PsiMember member = tryCast(ref.resolve(), PsiMember.class);
      if (member != null && ImportUtils.isStaticallyImported(member, ref)) {
        aClass = member.getContainingClass();
      }
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(ref.getProject());
    PsiIdentifier identifier = factory.createIdentifier(newName);
    nameElement.replace(identifier);
    if(aClass != null) {
      PsiMember member = tryCast(ref.resolve(), PsiMember.class);
      if (member == null || member.getContainingClass() != aClass) {
        ref.setQualifierExpression(factory.createReferenceExpression(aClass));
      }
    }
  }

  /**
   * Bind method call to a new name. Type arguments and call arguments remain the same.
   * The qualifier remains the same unless the original unqualified reference resolves
   * to statically imported member. In this case the qualifier could be added.
   *
   * @param call to rename
   * @param newName new name
   */
  public static void bindCallTo(@NotNull PsiMethodCallExpression call, @NotNull @NonNls String newName) {
    bindReferenceTo(call.getMethodExpression(), newName);
  }

  /**
   * Returns the expression itself (probably with stripped parentheses) or the corresponding value if the expression is a local variable
   * reference which is initialized and not used anywhere else
   *
   * @return a resolved expression or expression itself
   */
  @Contract("null -> null")
  @Nullable
  public static PsiExpression resolveExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiReferenceExpression reference &&
        reference.resolve() instanceof PsiLocalVariable variable &&
        !(variable instanceof PsiResourceVariable)) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null && ReferencesSearch.search(variable).allMatch(ref -> ref == reference)) {
        return initializer;
      }
    }
    return expression;
  }

  @Contract("null -> null")
  @Nullable
  public static PsiLocalVariable resolveLocalVariable(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
    if(referenceExpression == null) return null;
    return tryCast(referenceExpression.resolve(), PsiLocalVariable.class);
  }

  @Contract("null -> null")
  @Nullable
  public static PsiVariable resolveVariable(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    PsiReferenceExpression referenceExpression = tryCast(expression, PsiReferenceExpression.class);
    if(referenceExpression == null) return null;
    return tryCast(referenceExpression.resolve(), PsiVariable.class);
  }

  public static boolean isOctalLiteral(PsiLiteralExpression literal) {
    final PsiType type = literal.getType();
    if (!PsiTypes.intType().equals(type) && !PsiTypes.longType().equals(type)) {
      return false;
    }
    if (literal.getValue() == null) {
      // red code
      return false;
    }
    return isOctalLiteralText(literal.getText());
  }

  public static boolean isOctalLiteralText(String literalText) {
    if (literalText.charAt(0) != '0' || literalText.length() < 2) {
      return false;
    }
    final char c1 = literalText.charAt(1);
    return c1 == '_' || (c1 >= '0' && c1 <= '7');
  }

  @Contract("null, _ -> false")
  public static boolean isMatchingChildAlwaysExecuted(@Nullable PsiExpression root, @NotNull Predicate<? super PsiExpression> matcher) {
    if (root == null) return false;
    AtomicBoolean result = new AtomicBoolean(false);
    root.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        super.visitExpression(expression);
        if (matcher.test(expression)) {
          result.set(true);
          stopWalking();
        }
      }

      @Override
      public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
        if (isMatchingChildAlwaysExecuted(expression.getCondition(), matcher) ||
            (isMatchingChildAlwaysExecuted(expression.getThenExpression(), matcher) &&
             isMatchingChildAlwaysExecuted(expression.getElseExpression(), matcher))) {
          result.set(true);
          stopWalking();
        }
      }

      @Override
      public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
        IElementType type = expression.getOperationTokenType();
        if (type.equals(JavaTokenType.OROR) || type.equals(JavaTokenType.ANDAND)) {
          PsiExpression firstOperand = ArrayUtil.getFirstElement(expression.getOperands());
          if (isMatchingChildAlwaysExecuted(firstOperand, matcher)) {
            result.set(true);
            stopWalking();
          }
        }
        else {
          super.visitPolyadicExpression(expression);
        }
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {}

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}
    });
    return result.get();
  }

  /**
   * @param expression expression to test
   * @return true if the expression return value is a new object which is guaranteed to be distinct from any other object created
   * in the program.
   */
  @Contract("null -> false")
  public static boolean isNewObject(@Nullable PsiExpression expression) {
    return expression != null && nonStructuralChildren(expression).allMatch(call -> {
      if (call instanceof PsiNewExpression) return true;
      if (call instanceof PsiArrayInitializerExpression) return true;
      if (call instanceof PsiMethodCallExpression) {
        ContractReturnValue returnValue =
          JavaMethodContractUtil.getNonFailingReturnValue(JavaMethodContractUtil.getMethodCallContracts((PsiCallExpression)call));
        return ContractReturnValue.returnNew().equals(returnValue);
      }
      return false;
    });
  }

  /**
   * Checks whether diff-expression represents a difference between from-expression and to-expression
   *
   * @param from from-expression
   * @param to   to-expression
   * @param diff diff-expression
   * @return true if diff = to - from
   */
  public static boolean isDifference(@NotNull PsiExpression from, @NotNull PsiExpression to, @NotNull PsiExpression diff) {
    diff = PsiUtil.skipParenthesizedExprDown(diff);
    if (diff == null) return false;

    EquivalenceChecker eq = EquivalenceChecker.getCanonicalPsiEquivalence();
    if (isZero(from) && eq.expressionsAreEquivalent(to, diff)) return true;
    if (isZero(diff) && eq.expressionsAreEquivalent(to, from)) return true;

    if (to instanceof PsiPolyadicExpression && from instanceof PsiPolyadicExpression) {
      final Pair<@NotNull PsiExpression, @NotNull PsiExpression> polyadicDiff = getPolyadicDiff((PsiPolyadicExpression)from,
                                                                                                (PsiPolyadicExpression)to);
      from = polyadicDiff.first;
      to = polyadicDiff.second;
    }
    if (diff instanceof PsiBinaryExpression && ((PsiBinaryExpression)diff).getOperationTokenType().equals(JavaTokenType.MINUS)) {
      PsiExpression left = ((PsiBinaryExpression)diff).getLOperand();
      PsiExpression right = ((PsiBinaryExpression)diff).getROperand();
      if (right != null && eq.expressionsAreEquivalent(to, left) && eq.expressionsAreEquivalent(from, right)) {
        return true;
      }
    }
    if (from instanceof PsiBinaryExpression && ((PsiBinaryExpression)from).getOperationTokenType().equals(JavaTokenType.MINUS)) {
      PsiExpression left = ((PsiBinaryExpression)from).getLOperand();
      PsiExpression right = ((PsiBinaryExpression)from).getROperand();
      if (right != null && eq.expressionsAreEquivalent(to, left) && eq.expressionsAreEquivalent(diff, right)) {
        return true;
      }
    }
    if (to instanceof PsiBinaryExpression && ((PsiBinaryExpression)to).getOperationTokenType().equals(JavaTokenType.PLUS)) {
      PsiExpression left = ((PsiBinaryExpression)to).getLOperand();
      PsiExpression right = ((PsiBinaryExpression)to).getROperand();
      if (right != null && eq.expressionsAreEquivalent(left, from) && eq.expressionsAreEquivalent(right, diff) ||
          (eq.expressionsAreEquivalent(right, from) && eq.expressionsAreEquivalent(left, diff))) {
        return true;
      }
    }
    Integer fromConstant = tryCast(computeConstantExpression(from), Integer.class);
    if (fromConstant == null) return false;
    Integer toConstant = tryCast(computeConstantExpression(to), Integer.class);
    if (toConstant == null) return false;
    Integer diffConstant = tryCast(computeConstantExpression(diff), Integer.class);
    if (diffConstant == null) return false;
    return diffConstant == toConstant - fromConstant;
  }

  /**
   * Get a diff from two {@link PsiPolyadicExpression} instances using {@link EquivalenceChecker}.
   * @param from the first expression to examine
   * @param to the second expression to examine
   * @return a pair of expressions without common parts if the original expressions had any,
   * or the original expressions if no common parts were found
   */
  @NotNull
  private static Pair<@NotNull PsiExpression, @NotNull PsiExpression> getPolyadicDiff(@NotNull final PsiPolyadicExpression from,
                                                                                      @NotNull final PsiPolyadicExpression to) {
    final EquivalenceChecker eq = EquivalenceChecker.getCanonicalPsiEquivalence();
    final EquivalenceChecker.Match match = eq.expressionsMatch(from, to);
    if (match.isPartialMatch()) {
      final PsiExpression leftDiff = PsiUtil.skipParenthesizedExprDown((PsiExpression)match.getLeftDiff());
      final PsiExpression rightDiff = PsiUtil.skipParenthesizedExprDown((PsiExpression)match.getRightDiff());

      if (leftDiff == null || rightDiff == null) return Pair.create(from, to);

      final PsiPolyadicExpression leftParent = PsiTreeUtil.getParentOfType(leftDiff, PsiPolyadicExpression.class);
      assert leftParent != null;

      final IElementType op = leftParent.getOperationTokenType();
      if (op == JavaTokenType.MINUS || op == JavaTokenType.PLUS) {
        if (shouldBeInverted(leftDiff, from)) return Pair.create(rightDiff, leftDiff);
        else return Pair.create(leftDiff, rightDiff);
      }
    }
    return Pair.create(from, to);
  }

  /**
   * The method checks all the algebraic rules for subtraction to decide
   * if the operand comes into the expression with the negative or positive sign
   * by traversing the PSI tree going up to the specified element.
   *
   * @param start the operand to check the sign for
   * @param end the end element to stop traversal
   * @return true if the operand comes into the expression with the positive sign, otherwise false
   */
  private static boolean shouldBeInverted(@NotNull PsiElement start, @NotNull final PsiElement end) {
    boolean result = false;
    PsiElement parent = start;
    while (parent != end) {
      start = parent;
      parent = parent.getParent();
      if (!(parent instanceof PsiPolyadicExpression)) continue;

      final IElementType op = ((PsiPolyadicExpression)parent).getOperationTokenType();
      if (op == JavaTokenType.MINUS && parent.getFirstChild() != start) {
        result = !result;
      }
    }
    return result;
  }

  /**
   * Returns an expression which represents an array element with given index if array is known to be never modified
   * after initialization.
   *
   * @param array an array variable
   * @param index an element index
   * @return an expression or null if index is out of bounds or array could be modified after initialization
   */
  @Nullable
  public static PsiExpression getConstantArrayElement(PsiVariable array, int index) {
    if (index < 0) return null;
    PsiExpression[] elements = getConstantArrayElements(array);
    if (elements == null || index >= elements.length) return null;
    return elements[index];
  }

  /**
   * Returns an array of expressions which represent all array elements if array is known to be never modified
   * after initialization.
   *
   * @param array an array variable
   * @return an array or null if array could be modified after initialization
   * (empty array means that the initializer is known to be an empty array).
   */
  public static PsiExpression @Nullable [] getConstantArrayElements(PsiVariable array) {
    PsiExpression initializer = array.getInitializer();
    if (initializer instanceof PsiNewExpression) initializer = ((PsiNewExpression)initializer).getArrayInitializer();
    if (!(initializer instanceof PsiArrayInitializerExpression)) return null;
    PsiExpression[] initializers = ((PsiArrayInitializerExpression)initializer).getInitializers();
    if (array instanceof PsiField && !(array.hasModifierProperty(PsiModifier.PRIVATE) && array.hasModifierProperty(PsiModifier.STATIC))) {
      return null;
    }
    Boolean isConstantArray = CachedValuesManager.<Boolean>getCachedValue(array, () -> CachedValueProvider.Result
      .create(isConstantArray(array), PsiModificationTracker.MODIFICATION_COUNT));
    Arrays.asList(initializers).replaceAll(expr -> isIllegalReference(array, expr) ? null : expr);
    return Boolean.TRUE.equals(isConstantArray) ? initializers : null;
  }

  private static boolean isIllegalReference(PsiVariable array, PsiExpression expr) {
    return SyntaxTraverser.psiTraverser(expr).filter(PsiReferenceExpression.class)
             .find(ref -> {
               PsiElement target = ref.resolve();
               return target == array ||
                      target instanceof PsiField && HighlightUtil.isIllegalForwardReferenceToField(ref, (PsiField)target, true) != null;
             }) != null;
  }

  private static boolean isConstantArray(PsiVariable array) {
    PsiElement scope = PsiTreeUtil.getParentOfType(array, array instanceof PsiField ? PsiClass.class : PsiCodeBlock.class);
    if (scope == null) return false;
    return PsiTreeUtil.processElements(scope, e -> {
      if (!(e instanceof PsiReferenceExpression ref)) return true;
      if (!ref.isReferenceTo(array)) return true;
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
      if (parent instanceof PsiForeachStatement && PsiTreeUtil.isAncestor(((PsiForeachStatement)parent).getIteratedValue(), ref, false)) {
        return true;
      }
      if (parent instanceof PsiReferenceExpression) {
        if (isReferenceTo(getArrayFromLengthExpression((PsiExpression)parent), array)) return true;
        if (parent.getParent() instanceof PsiMethodCallExpression &&
            MethodCallUtils.isCallToMethod((PsiMethodCallExpression)parent.getParent(), CommonClassNames.JAVA_LANG_OBJECT,
                                           null, "clone", PsiType.EMPTY_ARRAY)) {
          return true;
        }
      }
      return parent instanceof PsiArrayAccessExpression && !PsiUtil.isAccessedForWriting((PsiExpression)parent);
    });
  }

  /**
   * Returns true if expression result depends only on local variable values, so it does not change
   * if locals don't change.
   *
   * @param expression expression to check
   * @return true if expression result depends only on local variable values
   */
  public static boolean isLocallyDefinedExpression(PsiExpression expression) {
    return PsiTreeUtil.processElements(expression, e -> {
      if (e instanceof PsiCallExpression) return false;
      if (e instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiField field) {
        return field.hasModifierProperty(PsiModifier.FINAL);
      }
      return !(e instanceof PsiArrayAccessExpression);
    });
  }

  /**
   * Tries to find the range inside the expression (relative to its start) which represents the given substring
   * assuming the expression evaluates to String.
   *
   * @param expression expression to find the range in
   * @param from start offset of substring in the String value of the expression
   * @param to end offset of substring in the String value of the expression
   * @return found range or null, if it cannot be found
   */
  @Nullable
  @Contract(value = "null, _, _ -> null", pure = true)
  public static TextRange findStringLiteralRange(PsiExpression expression, int from, int to) {
    if (to < 0 || from > to) return null;
    if (expression == null || !TypeUtils.isJavaLangString(expression.getType())) return null;
    if (expression instanceof PsiLiteralExpression literalExpression) {
      String value = tryCast(literalExpression.getValue(), String.class);
      if (value == null || value.length() < from || value.length() < to) return null;
      String text = expression.getText();
      if (literalExpression.isTextBlock()) {
        int indent = PsiLiteralUtil.getTextBlockIndent(literalExpression);
        if (indent == -1) return null;
        return PsiLiteralUtil.mapBackTextBlockRange(text, from, to, indent);
      }
      return PsiLiteralUtil.mapBackStringRange(expression.getText(), from, to);
    }
    if (expression instanceof PsiParenthesizedExpression) {
      PsiExpression operand = ((PsiParenthesizedExpression)expression).getExpression();
      TextRange range = findStringLiteralRange(operand, from, to);
      return range == null ? null : range.shiftRight(operand.getStartOffsetInParent());
    }
    if (expression instanceof PsiPolyadicExpression concatenation) {
      if (concatenation.getOperationTokenType() != JavaTokenType.PLUS) return null;
      PsiExpression[] operands = concatenation.getOperands();
      for (PsiExpression operand : operands) {
        Object constantValue = computeConstantExpression(operand);
        if (constantValue == null) return null;
        String stringValue = constantValue.toString();
        if (from < stringValue.length()) {
          if (to > stringValue.length()) return null;
          TextRange range = findStringLiteralRange(operand, from, to);
          return range == null ? null : range.shiftRight(operand.getStartOffsetInParent());
        }
        from -= stringValue.length();
        to -= stringValue.length();
      }
    }
    return null;
  }

  /**
   * Returns true if expression is evaluated in void context (i.e. its return value is not used)
   * @param expression expression to check
   * @return true if expression is evaluated in void context.
   */
  public static boolean isVoidContext(PsiExpression expression) {
    PsiElement element = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (element instanceof PsiExpressionStatement) {
      return !(element.getParent() instanceof PsiSwitchLabeledRuleStatement ruleStatement) ||
             !(ruleStatement.getEnclosingSwitchBlock() instanceof PsiSwitchExpression);
    }
    if (element instanceof PsiExpressionList && element.getParent() instanceof PsiExpressionListStatement) {
      return true;
    }
    if (element instanceof PsiLambdaExpression lambda) {
      return PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType(lambda));
    }
    return false;
  }

  /**
   * Looks for expression which given expression is compared to (either with ==, != or {@code equals()} call)
   *
   * @param expression expression which is compared to something else
   * @return another expression the supplied one is compared to or null if comparison is not detected
   */
  @Nullable
  public static PsiExpression getExpressionComparedTo(@NotNull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiBinaryExpression binOp && ComparisonUtils.isEqualityComparison(binOp)) {
      PsiExpression leftOperand = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
      PsiExpression rightOperand = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
      return PsiTreeUtil.isAncestor(leftOperand, expression, false) ? rightOperand : leftOperand;
    }
    if (parent instanceof PsiExpressionList &&
        parent.getParent() instanceof PsiMethodCallExpression call &&
        MethodCallUtils.isEqualsCall(call)) {
      return PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
    }
    PsiMethodCallExpression call = getCallForQualifier(expression);
    if (call != null && MethodCallUtils.isEqualsCall(call)) {
      return PsiUtil.skipParenthesizedExprDown(ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()));
    }
    return null;
  }

  /**
   * Returns ancestor expression for given subexpression which parent is not an expression anymore (except lambda)
   *
   * @param expression an expression to find its ancestor
   * @return a top-level expression for given expression (may return an expression itself)
   */
  @NotNull
  public static PsiExpression getTopLevelExpression(@NotNull PsiExpression expression) {
    while(true) {
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpression && !(parent instanceof PsiLambdaExpression)) {
        expression = (PsiExpression)parent;
      } else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiExpression) {
        expression = (PsiExpression)parent.getParent();
      } else {
        return expression;
      }
    }
  }

  public static PsiElement getPassThroughParent(@NotNull PsiExpression expression) {
    return getPassThroughExpression(expression).getParent();
  }

  public static @NotNull PsiExpression getPassThroughExpression(@NotNull PsiExpression expression) {
    while (true) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression) {
        expression = (PsiExpression)parent;
        continue;
      }
      else if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != expression) {
        expression = (PsiExpression)parent;
        continue;
      }
      else if (parent instanceof PsiExpressionStatement) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiSwitchLabeledRuleStatement) {
          final PsiSwitchBlock block = ((PsiSwitchLabeledRuleStatement)grandParent).getEnclosingSwitchBlock();
          if (block instanceof PsiSwitchExpression) {
            expression = (PsiExpression)block;
            continue;
          }
        }
      }
      else if (parent instanceof PsiYieldStatement) {
        PsiSwitchExpression enclosing = ((PsiYieldStatement)parent).findEnclosingExpression();
        if (enclosing != null) {
          expression = enclosing;
          continue;
        }
      }
      return expression;
    }
  }

  public static boolean isImplicitToStringCall(PsiExpression expression) {
    if (isStringConcatenationOperand(expression)) return true;

    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    while (parent instanceof PsiConditionalExpression &&
           !PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), expression, false)) {
      parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    }

    if (parent instanceof PsiExpressionList expressionList &&
        expressionList.getParent() instanceof PsiMethodCallExpression call &&
        IMPLICIT_TO_STRING_METHOD_NAMES.contains(call.getMethodExpression().getReferenceName())) {
      final PsiExpression[] arguments = expressionList.getExpressions();
      final PsiMethod method = call.resolveMethod();
      if (method == null) return false;
      @NonNls final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return false;
      final String className = containingClass.getQualifiedName();
      if (className == null) return false;
      return switch (methodName) {
        case "append" -> {
          if (arguments.length != 1) yield false;
          if (!className.equals(CommonClassNames.JAVA_LANG_STRING_BUILDER) &&
              !className.equals(CommonClassNames.JAVA_LANG_STRING_BUFFER)) {
            yield false;
          }
          yield !hasCharArrayParameter(method);
        }
        case "valueOf" -> {
          if (arguments.length != 1 || !CommonClassNames.JAVA_LANG_STRING.equals(className)) yield false;
          yield !hasCharArrayParameter(method);
        }
        case "print", "println" -> {
          if (arguments.length != 1 || hasCharArrayParameter(method)) yield false;
          yield "java.util.Formatter".equals(className) ||
                InheritanceUtil.isInheritor(containingClass, "java.io.PrintStream") ||
                InheritanceUtil.isInheritor(containingClass, "java.io.PrintWriter");
        }
        case "printf", "format" -> {
          if (arguments.length < 1) yield false;
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length == 0) yield false;
          final PsiParameter parameter = parameters[0];
          final PsiType firstParameterType = parameter.getType();
          final int minArguments = firstParameterType.equalsToText("java.util.Locale") ? 4 : 3;
          if (arguments.length < minArguments) yield false;
          yield CommonClassNames.JAVA_LANG_STRING.equals(className) || "java.util.Formatter".equals(className) ||
                InheritanceUtil.isInheritor(containingClass, "java.io.PrintStream") ||
                InheritanceUtil.isInheritor(containingClass, "java.io.PrintWriter");
        }
        default -> false;
      };
    }
    return false;
  }

  private static boolean hasCharArrayParameter(PsiMethod method) {
    @NonNls final PsiParameter parameter = ArrayUtil.getFirstElement(method.getParameterList().getParameters());
    return parameter == null || parameter.getType().equalsToText("char[]");
  }

  /**
   * Convert initializer expression to a normal expression that could be used in another context.
   * Currently, the only case when initializer cannot be used in another context is array initializer:
   * in this case it's necessary to add explicit array creation like {@code new ArrayType[] {...}}.
   *
   * <p>
   * If conversion is required a non-physical expression is created without affecting the original expression.
   * No write action is required.
   * @param initializer initializer to convert
   * @param factory element factory to use
   * @param type expected expression type
   * @return the converted expression. May return the original expression if conversion is not necessary.
   */
  @Contract("null, _, _ -> null")
  public static PsiExpression convertInitializerToExpression(@Nullable PsiExpression initializer,
                                                             @NotNull PsiElementFactory factory,
                                                             @Nullable PsiType type) {
    if (initializer instanceof PsiArrayInitializerExpression && type instanceof PsiArrayType) {
      PsiNewExpression result =
        (PsiNewExpression)factory.createExpressionFromText("new " + type.getCanonicalText() + "{}", null);
      Objects.requireNonNull(result.getArrayInitializer()).replace(initializer);
      return result;
    }
    return initializer;
  }

  /**
   * Splits variable declaration and initialization. Currently works for single variable declarations only. Requires write action.
   *
   * @param declaration declaration to split
   * @param project current project.
   * @return the assignment expression created if the declaration was successfully split.
   * In this case, the declaration is still valid and could be used afterwards.
   * Returns null if the splitting wasn't successful (no changes in the document are performed in this case).
   */
  @Nullable
  public static PsiAssignmentExpression splitDeclaration(@NotNull PsiDeclarationStatement declaration, @NotNull Project project) {
    if (declaration.getDeclaredElements().length == 1) {
      PsiLocalVariable var = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      var.normalizeDeclaration();
      final PsiTypeElement typeElement = var.getTypeElement();
      if (typeElement.isInferredType()) {
        PsiTypesUtil.replaceWithExplicitType(typeElement);
      }
      final String name = var.getName();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(name + "=xxx;", declaration);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(project).reformat(statement);
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
      PsiExpression initializer = var.getInitializer();
      assert initializer != null;
      PsiExpression rExpression = convertInitializerToExpression(initializer, factory, var.getType());

      final PsiExpression expression = assignment.getRExpression();
      assert expression != null;
      expression.replace(rExpression);

      PsiElement block = declaration.getParent();
      if (block instanceof PsiForStatement) {
        final PsiDeclarationStatement varDeclStatement =
          factory.createVariableDeclarationStatement(name, var.getType(), null);

        // For index can't be final, right?
        for (PsiElement varDecl : varDeclStatement.getDeclaredElements()) {
          if (varDecl instanceof PsiModifierListOwner) {
            final PsiModifierList modList = ((PsiModifierListOwner)varDecl).getModifierList();
            assert modList != null;
            modList.setModifierProperty(PsiModifier.FINAL, false);
          }
        }

        final PsiElement parent = block.getParent();
        PsiExpressionStatement replaced = (PsiExpressionStatement)new CommentTracker().replaceAndRestoreComments(declaration, statement);
        if (!(parent instanceof PsiCodeBlock)) {
          final PsiBlockStatement blockStatement =
            (PsiBlockStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText("{}", block);
          final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
          codeBlock.add(varDeclStatement);
          codeBlock.add(block);
          block.replace(blockStatement);
        }
        else {
          parent.addBefore(varDeclStatement, block);
        }
        return (PsiAssignmentExpression)replaced.getExpression();
      }
      else {
        try {
          PsiElement declaredElement = declaration.getDeclaredElements()[0];
          if (!PsiUtil.isJavaToken(declaredElement.getLastChild(), JavaTokenType.SEMICOLON)) {
            TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, declaration.getManager());
            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(declaration.addAfter(semicolon.getPsi(), declaredElement));
          }
          return (PsiAssignmentExpression)((PsiExpressionStatement)block.addAfter(statement, declaration)).getExpression();
        }
        finally {
          initializer = var.getInitializer();
          if (initializer != null) {
            initializer.delete();
          }
        }
      }
    }
    else {
      ((PsiLocalVariable)declaration.getDeclaredElements()[0]).normalizeDeclaration();
    }
    return null;
  }

  /**
   * @param expression expression to test
   * @param loopStatement loop statement
   * @return true if given expression is likely to be a loop invariant. False if it's not invariant, or not known.
   */
  public static boolean isLoopInvariant(PsiExpression expression, @SuppressWarnings("unused") PsiLoopStatement loopStatement) {
    if (PsiUtil.isConstantExpression(expression)) return true;
    if (SideEffectChecker.mayHaveSideEffects(expression)) return false;
    Collection<PsiReferenceExpression> refs = PsiTreeUtil.collectElementsOfType(expression, PsiReferenceExpression.class);
    for (PsiReferenceExpression ref : refs) {
      PsiElement target = ref.resolve();
      // TODO: more sophisticated analysis
      if (target instanceof PsiField && ((PsiField)target).hasModifierProperty(PsiModifier.FINAL)) continue;
      if (target instanceof PsiLocalVariable || target instanceof PsiParameter) {
        PsiVariable var = (PsiVariable)target;
        if (var.hasModifierProperty(PsiModifier.FINAL) ||
            HighlightControlFlowUtil.isEffectivelyFinal(var, PsiUtil.getVariableCodeBlock(var, null), null)) {
          continue;
        }
      }
      return false;
    }
    return true;
  }

  /**
   * @param expression  the expression to check
   * @return true, if the specified expression is the only expression in the method (it's value is returned by the method). False otherwise.
   */
  public static boolean isOnlyExpressionInMethod(@NotNull PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiCodeBlock codeBlock) || !(grandParent.getParent() instanceof PsiMethod)) {
      return false;
    }
    return codeBlock.getStatementCount() == 1;
  }
}