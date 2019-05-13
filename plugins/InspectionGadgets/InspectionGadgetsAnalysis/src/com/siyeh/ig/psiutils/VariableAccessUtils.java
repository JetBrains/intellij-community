/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VariableAccessUtils {

  private VariableAccessUtils() {}

  public static boolean variableIsAssignedFrom(@NotNull PsiVariable variable,
                                               @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableAssignedFromVisitor visitor =
      new VariableAssignedFromVisitor(variable);
    context.accept(visitor);
    return visitor.isAssignedFrom();
  }

  public static boolean variableIsPassedAsMethodArgument(
    @NotNull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariablePassedAsArgumentVisitor visitor =
      new VariablePassedAsArgumentVisitor(variable);
    context.accept(visitor);
    return visitor.isPassed();
  }

  public static boolean variableIsPassedAsMethodArgument(@NotNull PsiVariable variable, @Nullable PsiElement context,
                                                         Processor<? super PsiCall> callProcessor) {
    return variableIsPassedAsMethodArgument(variable, context, false, callProcessor);
  }

  public static boolean variableIsPassedAsMethodArgument(@NotNull PsiVariable variable, @Nullable PsiElement context,
                                                         boolean builderPattern, Processor<? super PsiCall> callProcessor) {
    if (context == null) {
      return false;
    }
    final VariablePassedAsArgumentExcludedVisitor visitor =
      new VariablePassedAsArgumentExcludedVisitor(variable, builderPattern, callProcessor);
    context.accept(visitor);
    return visitor.isPassed();
  }

  public static boolean variableIsUsedInArrayInitializer(
    @NotNull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableUsedInArrayInitializerVisitor visitor =
      new VariableUsedInArrayInitializerVisitor(variable);
    context.accept(visitor);
    return visitor.isPassed();
  }

  /**
   * This method will return true if the specified variable is a field with greater than private visibility with a common name.
   * Finding usages for such fields is too expensive.
   * @param variable  the variable to check assignments for
   * @return true, if the variable is assigned or too expensive to search. False otherwise.
   */
  public static boolean variableIsAssigned(@NotNull PsiVariable variable) {
    if (variable instanceof PsiField) {
      if (variable.hasModifierProperty(PsiModifier.PRIVATE)) {
        final PsiClass aClass = PsiUtil.getTopLevelClass(variable);
        return variableIsAssigned(variable, aClass);
      }
      return DeclarationSearchUtils.isTooExpensiveToSearch(variable, false) || ReferencesSearch.search(variable).anyMatch(reference -> {
        final PsiExpression expression = ObjectUtils.tryCast(reference.getElement(), PsiExpression.class);
        return expression != null && PsiUtil.isAccessedForWriting(expression);
      });
    }
    final PsiElement context =
      PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiMethod.class, PsiLambdaExpression.class,
                                  PsiCatchSection.class, PsiForStatement.class, PsiForeachStatement.class);
    return variableIsAssigned(variable, context);
  }

  public static boolean variableIsAssigned(@NotNull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variable, true);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  public static boolean variableIsAssigned(
    @NotNull PsiVariable variable, @Nullable PsiElement context,
    boolean recurseIntoClasses) {
    if (context == null) {
      return false;
    }
    final VariableAssignedVisitor visitor =
      new VariableAssignedVisitor(variable, recurseIntoClasses);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  public static boolean variableIsReturned(@NotNull PsiVariable variable, @Nullable PsiElement context) {
    return variableIsReturned(variable, context, false);
  }

  public static boolean variableIsReturned(@NotNull PsiVariable variable, @Nullable PsiElement context, boolean builderPattern) {
    if (context == null) {
      return false;
    }
    final VariableReturnedVisitor visitor = new VariableReturnedVisitor(variable, builderPattern);
    context.accept(visitor);
    return visitor.isReturned();
  }

  public static boolean variableValueIsUsed(
    @NotNull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableValueUsedVisitor visitor =
      new VariableValueUsedVisitor(variable);
    context.accept(visitor);
    return visitor.isVariableValueUsed();
  }

  public static boolean arrayContentsAreAssigned(
    @NotNull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final ArrayContentsAssignedVisitor visitor =
      new ArrayContentsAssignedVisitor(variable);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  public static boolean variableIsUsedInInnerClass(
    @NotNull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableUsedInInnerClassVisitor visitor =
      new VariableUsedInInnerClassVisitor(variable);
    context.accept(visitor);
    return visitor.isUsedInInnerClass();
  }

  public static boolean mayEvaluateToVariable(@Nullable PsiExpression expression, @NotNull PsiVariable variable) {
    return mayEvaluateToVariable(expression, variable, false);
  }

  static boolean mayEvaluateToVariable(@Nullable PsiExpression expression, @NotNull PsiVariable variable, boolean builderPattern) {
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression containedExpression = parenthesizedExpression.getExpression();
      return mayEvaluateToVariable(containedExpression, variable, builderPattern);
    }
    if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
      final PsiExpression containedExpression = typeCastExpression.getOperand();
      return mayEvaluateToVariable(containedExpression, variable, builderPattern);
    }
    if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditional = (PsiConditionalExpression)expression;
      final PsiExpression thenExpression = conditional.getThenExpression();
      final PsiExpression elseExpression = conditional.getElseExpression();
      return mayEvaluateToVariable(thenExpression, variable, builderPattern) ||
             mayEvaluateToVariable(elseExpression, variable, builderPattern);
    }
    if (expression instanceof PsiArrayAccessExpression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiArrayAccessExpression) {
        return false;
      }
      final PsiType type = variable.getType();
      if (!(type instanceof PsiArrayType)) {
        return false;
      }
      final PsiArrayType arrayType = (PsiArrayType)type;
      final int dimensions = arrayType.getArrayDimensions();
      if (dimensions <= 1) {
        return false;
      }
      PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)expression;
      PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
      int count = 1;
      while (arrayExpression instanceof PsiArrayAccessExpression) {
        arrayAccessExpression = (PsiArrayAccessExpression)arrayExpression;
        arrayExpression = arrayAccessExpression.getArrayExpression();
        count++;
      }
      return count != dimensions && mayEvaluateToVariable(arrayExpression, variable, builderPattern);
    }
    if (builderPattern && expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiType returnType = method.getReturnType();
      final PsiType variableType = variable.getType();
      if (!variableType.equals(returnType)) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      return mayEvaluateToVariable(qualifier, variable, true);
    }
    return ExpressionUtils.isReferenceTo(expression, variable);
  }

  public static List<PsiReferenceExpression> getVariableReferences(@NotNull PsiVariable variable, @Nullable PsiElement context) {
    if (context == null) return Collections.emptyList();
    List<PsiReferenceExpression> result = new ArrayList<>();
    PsiTreeUtil.processElements(context, e -> {
      if (e instanceof PsiReferenceExpression && ((PsiReferenceExpression)e).isReferenceTo(variable)) {
        result.add((PsiReferenceExpression)e);
      }
      return true;
    });
    return result;
  }
  
  @Contract("_, null -> false")
  public static boolean variableIsUsed(@NotNull PsiVariable variable,
                                       @Nullable PsiElement context) {
    return context != null && VariableUsedVisitor.isVariableUsedIn(variable, context);
  }

  public static boolean variableIsDecremented(@NotNull PsiVariable variable, @Nullable PsiStatement statement) {
    return variableIsIncrementedOrDecremented(variable, statement, false);  }

  public static boolean variableIsIncremented(@NotNull PsiVariable variable, @Nullable PsiStatement statement) {
    return variableIsIncrementedOrDecremented(variable, statement, true);
  }

  private static boolean variableIsIncrementedOrDecremented(@NotNull PsiVariable variable, @Nullable PsiStatement statement,
                                                            boolean incremented) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement =
      (PsiExpressionStatement)statement;
    PsiExpression expression = expressionStatement.getExpression();
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiUnaryExpression) {
      final PsiUnaryExpression unaryExpression =
        (PsiUnaryExpression)expression;
      final IElementType tokenType = unaryExpression.getOperationTokenType();
      if (!tokenType.equals(incremented ? JavaTokenType.PLUSPLUS : JavaTokenType.MINUSMINUS)) {
        return false;
      }
      final PsiExpression operand = unaryExpression.getOperand();
      return ExpressionUtils.isReferenceTo(operand, variable);
    }
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)expression;
      final IElementType tokenType =
        assignmentExpression.getOperationTokenType();
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!ExpressionUtils.isReferenceTo(lhs, variable)) {
        return false;
      }
      PsiExpression rhs = assignmentExpression.getRExpression();
      rhs = ParenthesesUtils.stripParentheses(rhs);
      if (tokenType == JavaTokenType.EQ) {
        if (!(rhs instanceof PsiBinaryExpression)) {
          return false;
        }
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)rhs;
        final IElementType binaryTokenType =
          binaryExpression.getOperationTokenType();
        if (binaryTokenType != (incremented ? JavaTokenType.PLUS : JavaTokenType.MINUS)) {
          return false;
        }
        final PsiExpression lOperand = binaryExpression.getLOperand();
        final PsiExpression rOperand = binaryExpression.getROperand();
        if (ExpressionUtils.isOne(lOperand)) {
          return ExpressionUtils.isReferenceTo(rOperand, variable);
        }
        if (ExpressionUtils.isOne(rOperand)) {
          return ExpressionUtils.isReferenceTo(lOperand, variable);
        }
      }
      else if (tokenType == (incremented ? JavaTokenType.PLUSEQ : JavaTokenType.MINUSEQ)) {
        if (ExpressionUtils.isOne(rhs)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean variableIsAssignedBeforeReference(@NotNull PsiReferenceExpression referenceExpression,
                                                          @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable variable = (PsiVariable)target;
    return variableIsAssignedAtPoint(variable, context, referenceExpression);
  }

  public static boolean variableIsAssignedAtPoint(
    @NotNull PsiVariable variable, @Nullable PsiElement context,
    @NotNull PsiElement point) {
    if (context == null) {
      return false;
    }
    final PsiElement directChild =
      getDirectChildWhichContainsElement(context, point);
    if (directChild == null) {
      return false;
    }
    final PsiElement[] children = context.getChildren();
    for (PsiElement child : children) {
      if (child == directChild) {
        return variableIsAssignedAtPoint(variable, directChild, point);
      }
      if (variableIsAssigned(variable, child)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiElement getDirectChildWhichContainsElement(
    @NotNull PsiElement ancestor,
    @NotNull PsiElement descendant) {
    if (ancestor == descendant) {
      return null;
    }
    PsiElement child = descendant;
    PsiElement parent = child.getParent();
    while (!parent.equals(ancestor)) {
      child = parent;
      parent = child.getParent();
      if (parent == null) {
        return null;
      }
    }
    return child;
  }

  public static Set<PsiVariable> collectUsedVariables(PsiElement context) {
    if (context == null) {
      return Collections.emptySet();
    }
    final VariableCollectingVisitor visitor = new VariableCollectingVisitor();
    context.accept(visitor);
    return visitor.getUsedVariables();
  }

  public static boolean isAnyVariableAssigned(@NotNull Collection<? extends PsiVariable> variables, @Nullable PsiElement context) {
    if (context == null) {
      return false;
    }
    final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variables, true);
    context.accept(visitor);
    return visitor.isAssigned();
  }

  /**
   * Check if local variable has the same behavior as its initializer.
   */
  public static boolean localVariableIsCopy(@NotNull PsiLocalVariable variable) {
    return localVariableIsCopy(variable, ParenthesesUtils.stripParentheses(variable.getInitializer()));
  }

  /**
   * Check if local variable has the same behavior as given expression.
   */
  public static boolean localVariableIsCopy(@NotNull PsiLocalVariable variable, @Nullable PsiExpression expression) {
    if (expression instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)expression).getOperand();
      if (operand instanceof PsiReferenceExpression && RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)expression)) {
        expression = operand;
      }
    }
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)expression;
    final PsiVariable initialization = ObjectUtils.tryCast(reference.resolve(), PsiVariable.class);
    if (initialization == null) {
      return false;
    }
    if (!(initialization instanceof PsiResourceVariable) && variable instanceof PsiResourceVariable) {
      return false;
    }
    if (!(initialization instanceof PsiLocalVariable || initialization instanceof PsiParameter)) {
      if (!isFinalChain(reference) || ReferencesSearch.search(variable).findAll().size() != 1) {
        // only warn when variable is referenced once, to avoid warning when a field is cached in local variable
        // as in e.g. gnu.trove.TObjectHash#forEach()
        return false;
      }
    }
    final PsiCodeBlock containingScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    if (containingScope == null) {
      return false;
    }
    if (variableMayChange(containingScope, null, variable)) {
      return false;
    }
    if (variableMayChange(containingScope, PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression()), initialization)) {
      return false;
    }

    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(containingScope.getProject()).getResolveHelper();
    final String initializationName = initialization.getName();
    if (initializationName == null) {
      return false;
    }

    final boolean finalVariableIntroduction =
      !initialization.hasModifierProperty(PsiModifier.FINAL) && variable.hasModifierProperty(PsiModifier.FINAL) ||
      PsiUtil.isLanguageLevel8OrHigher(initialization) &&
      !HighlightControlFlowUtil.isEffectivelyFinal(initialization, containingScope, null) &&
      HighlightControlFlowUtil.isEffectivelyFinal(variable, containingScope, null);

    final PsiType variableType = variable.getType();
    final PsiType initializationType = initialization.getType();
    final boolean sameType = Comparing.equal(variableType, initializationType);
    for (PsiReference ref : ReferencesSearch.search(variable, new LocalSearchScope(containingScope))) {
      final PsiElement refElement = ref.getElement();
      if (finalVariableIntroduction) {
        final PsiElement element = PsiTreeUtil.getParentOfType(refElement, PsiClass.class, PsiLambdaExpression.class);
        if (element != null && PsiTreeUtil.isAncestor(containingScope, element, true)) {
          return false;
        }
      }

      if (resolveHelper.resolveReferencedVariable(initializationName, refElement) != initialization) {
        return false;
      }

      if (!sameType) {
        final PsiElement parent = refElement.getParent();
        if (parent instanceof PsiReferenceExpression) {
          final PsiElement resolve = ((PsiReferenceExpression)parent).resolve();
          if (resolve instanceof PsiMember &&
              ((PsiMember)resolve).hasModifierProperty(PsiModifier.PRIVATE)) {
            return false;
          }
        }
      }
    }

    return !TypeConversionUtil.boxingConversionApplicable(variableType, initializationType);
  }

  private static boolean isFinalChain(PsiReferenceExpression reference) {
    while (true) {
      PsiElement element = reference.resolve();
      if (!(element instanceof PsiField)) return true;
      if (!((PsiField)element).hasModifierProperty(PsiModifier.FINAL)) return false;
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression());
      if (qualifier == null || qualifier instanceof PsiThisExpression) return true;
      if (!(qualifier instanceof PsiReferenceExpression)) return false;
      reference = (PsiReferenceExpression)qualifier;
    }
  }

  private static boolean variableMayChange(PsiCodeBlock containingScope, PsiExpression qualifier, PsiVariable variable) {
    while (variable != null) {
      if (!variable.hasModifierProperty(PsiModifier.FINAL) &&
          variableIsAssigned(variable, containingScope, false)) {
        return true;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) break;
      PsiReferenceExpression qualifierReference = (PsiReferenceExpression)qualifier;
      qualifier = PsiUtil.skipParenthesizedExprDown(qualifierReference.getQualifierExpression());
      variable = ObjectUtils.tryCast(qualifierReference.resolve(), PsiVariable.class);
    }
    return false;
  }

  private static class VariableCollectingVisitor extends JavaRecursiveElementWalkingVisitor {

    private final Set<PsiVariable> usedVariables = new HashSet<>();

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiVariable)) {
        return;
      }
      final PsiVariable variable = (PsiVariable)target;
      usedVariables.add(variable);
    }

    public Set<PsiVariable> getUsedVariables() {
      return usedVariables;
    }
  }
}