/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SwitchUtils {

  private SwitchUtils() {}

  public static int calculateBranchCount(@NotNull PsiSwitchStatement statement) {
    final PsiCodeBlock body = statement.getBody();
    if (body == null) {
      return 0;
    }
    final PsiStatement[] statements = body.getStatements();
    int branches = 0;
    for (final PsiStatement child : statements) {
      if (child instanceof PsiSwitchLabelStatement) {
        branches++;
      }
    }
    return branches;
  }

  @Nullable
  public static PsiExpression getSwitchExpression(PsiIfStatement statement, int minimumBranches) {
    return getSwitchExpression(statement, minimumBranches, false);
  }

  @Nullable
  public static PsiExpression getSwitchExpression(PsiIfStatement statement, int minimumBranches, boolean nullSafe) {
    final PsiExpression condition = statement.getCondition();
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(statement);
    final PsiExpression possibleSwitchExpression = determinePossibleSwitchExpressions(condition, languageLevel, nullSafe);
    if (!canBeSwitchExpression(possibleSwitchExpression, languageLevel)) {
      return null;
    }
    int branchCount = 0;
    while (true) {
      branchCount++;
      if (!canBeMadeIntoCase(statement.getCondition(), possibleSwitchExpression, languageLevel, false)) {
        break;
      }
      final PsiStatement elseBranch = statement.getElseBranch();
      if (!(elseBranch instanceof PsiIfStatement)) {
        if (elseBranch != null) {
          branchCount++;
        }
        if (branchCount < minimumBranches) {
          return null;
        }
        return possibleSwitchExpression;
      }
      statement = (PsiIfStatement)elseBranch;
    }
    return null;
  }

  private static boolean canBeMadeIntoCase(PsiExpression expression, PsiExpression switchExpression, LanguageLevel languageLevel,
                                           boolean nullSafe) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiExpression stringSwitchExpression = determinePossibleStringSwitchExpression(expression, nullSafe);
      if (EquivalenceChecker.expressionsAreEquivalent(switchExpression, stringSwitchExpression)) {
        return true;
      }
    }
    if (!(expression instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
    final IElementType operation = polyadicExpression.getOperationTokenType();
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operation.equals(JavaTokenType.OROR)) {
      for (PsiExpression operand : operands) {
        if (!canBeMadeIntoCase(operand, switchExpression, languageLevel, nullSafe)) {
          return false;
        }
      }
      return true;
    }
    else if (operation.equals(JavaTokenType.EQEQ) && operands.length == 2) {
      return (canBeCaseLabel(operands[0], languageLevel) && EquivalenceChecker.expressionsAreEquivalent(switchExpression, operands[1])) ||
             (canBeCaseLabel(operands[1], languageLevel) && EquivalenceChecker.expressionsAreEquivalent(switchExpression, operands[0]));
    }
    else {
      return false;
    }
  }

  private static boolean canBeSwitchExpression(PsiExpression expression, LanguageLevel languageLevel) {
    if (expression == null || SideEffectChecker.mayHaveSideEffects(expression)) {
      return false;
    }
    final PsiType type = expression.getType();
    if (PsiType.CHAR.equals(type) || PsiType.BYTE.equals(type) || PsiType.SHORT.equals(type) || PsiType.INT.equals(type)) {
      return true;
    }
    else if (type instanceof PsiClassType) {
      if (isAnnotatedNullable(expression)) {
        return false;
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER) || type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
          type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) || type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)) {
        return true;
      }
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        if (aClass != null && aClass.isEnum()) {
          return true;
        }
      }
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7) && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return true;
      }
    }
    return false;
  }

  private static PsiExpression determinePossibleSwitchExpressions(PsiExpression expression, LanguageLevel languageLevel, boolean nullSafe) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression == null) {
      return null;
    }
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiExpression jdk17Expression = determinePossibleStringSwitchExpression(expression, nullSafe);
      if (jdk17Expression != null) {
        return jdk17Expression;
      }
    }
    if (!(expression instanceof PsiPolyadicExpression)) {
      return null;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
    final IElementType operation = polyadicExpression.getOperationTokenType();
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operation.equals(JavaTokenType.OROR) && operands.length > 0) {
      return determinePossibleSwitchExpressions(operands[0], languageLevel, nullSafe);
    }
    else if (operation.equals(JavaTokenType.EQEQ) && operands.length == 2) {
      final PsiExpression lhs = operands[0];
      final PsiExpression rhs = operands[1];
      if (canBeCaseLabel(lhs, languageLevel)) {
        return rhs;
      }
      else if (canBeCaseLabel(rhs, languageLevel)) {
        return lhs;
      }
    }
    return null;
  }

  private static PsiExpression determinePossibleStringSwitchExpression(PsiExpression expression, boolean nullSafe) {
    if (!(expression instanceof PsiMethodCallExpression)) {
      return null;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    @NonNls final String referenceName = methodExpression.getReferenceName();
    if (!"equals".equals(referenceName)) {
      return null;
    }
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    final PsiType type = qualifierExpression.getType();
    if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return null;
    }
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 1) {
      return null;
    }
    final PsiExpression argument = arguments[0];
    final PsiType argumentType = argument.getType();
    if (argumentType == null || !argumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return null;
    }
    if (PsiUtil.isConstantExpression(qualifierExpression)) {
      if (nullSafe && !isAnnotatedNotNull(argument)) {
        return null;
      }
      return argument;
    }
    else if (PsiUtil.isConstantExpression(argument)) {
      return qualifierExpression;
    }
    return null;
  }

  private static boolean isAnnotatedNotNull(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiModifierListOwner)) {
      return false;
    }
    final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)target;
    return NullableNotNullManager.isNotNull(modifierListOwner);
  }

  private static boolean isAnnotatedNullable(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiModifierListOwner)) {
      return false;
    }
    final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)target;
    return NullableNotNullManager.isNullable(modifierListOwner);
  }

  private static boolean canBeCaseLabel(PsiExpression expression, LanguageLevel languageLevel) {
    if (expression == null) {
      return false;
    }
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && expression instanceof PsiReferenceExpression) {
      final PsiElement referent = ((PsiReference)expression).resolve();
      if (referent instanceof PsiEnumConstant) {
        return true;
      }
    }
    final PsiType type = expression.getType();
    return (PsiType.INT.equals(type) || PsiType.SHORT.equals(type) || PsiType.BYTE.equals(type) || PsiType.CHAR.equals(type)) &&
           PsiUtil.isConstantExpression(expression);
  }

  public static String findUniqueLabelName(PsiStatement statement, @NonNls String baseName) {
    final PsiElement ancestor = PsiTreeUtil.getParentOfType(statement, PsiMember.class);
    if (!checkForLabel(baseName, ancestor)) {
      return baseName;
    }
    int val = 1;
    while (true) {
      final String name = baseName + val;
      if (!checkForLabel(name, ancestor)) {
        return name;
      }
      val++;
    }
  }

  private static boolean checkForLabel(String name, PsiElement ancestor) {
    final LabelSearchVisitor visitor = new LabelSearchVisitor(name);
    ancestor.accept(visitor);
    return visitor.isUsed();
  }

  private static class LabelSearchVisitor extends JavaRecursiveElementWalkingVisitor {

    private final String m_labelName;
    private boolean m_used = false;

    LabelSearchVisitor(String name) {
      m_labelName = name;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (m_used) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitLabeledStatement(PsiLabeledStatement statement) {
      final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      final String labelText = labelIdentifier.getText();
      if (labelText.equals(m_labelName)) {
        m_used = true;
      }
    }

    public boolean isUsed() {
      return m_used;
    }
  }

  public static class IfStatementBranch {

    private final Set<String> topLevelVariables = new HashSet<String>(3);
    private final LinkedList<String> comments = new LinkedList<String>();
    private final LinkedList<String> statementComments = new LinkedList<String>();
    private final List<PsiExpression> caseExpressions = new ArrayList<PsiExpression>(3);
    private final PsiStatement statement;
    private final boolean elseBranch;

    public IfStatementBranch(PsiStatement branch, boolean elseBranch) {
      statement = branch;
      this.elseBranch = elseBranch;
      calculateVariablesDeclared(statement);
    }

    public void addComment(String comment) {
      comments.addFirst(comment);
    }

    public void addStatementComment(String comment) {
      statementComments.addFirst(comment);
    }

    public void addCaseExpression(PsiExpression expression) {
      caseExpressions.add(expression);
    }

    public PsiStatement getStatement() {
      return statement;
    }

    public List<PsiExpression> getCaseExpressions() {
      return Collections.unmodifiableList(caseExpressions);
    }

    public boolean isElse() {
      return elseBranch;
    }

    public boolean topLevelDeclarationsConflictWith(IfStatementBranch testBranch) {
      final Set<String> topLevel = testBranch.topLevelVariables;
      return intersects(topLevelVariables, topLevel);
    }

    private static boolean intersects(Set<String> set1, Set<String> set2) {
      for (final String s : set1) {
        if (set2.contains(s)) {
          return true;
        }
      }
      return false;
    }

    public List<String> getComments() {
      return comments;
    }

    public List<String> getStatementComments() {
      return statementComments;
    }

    public void calculateVariablesDeclared(PsiStatement statement) {
      if (statement == null) {
        return;
      }
      if (statement instanceof PsiDeclarationStatement) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
        final PsiElement[] elements = declarationStatement.getDeclaredElements();
        for (PsiElement element : elements) {
          final PsiVariable variable = (PsiVariable)element;
          final String varName = variable.getName();
          topLevelVariables.add(varName);
        }
      }
      else if (statement instanceof PsiBlockStatement) {
        final PsiBlockStatement block = (PsiBlockStatement)statement;
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement1 : statements) {
          calculateVariablesDeclared(statement1);
        }
      }
    }
  }
}
