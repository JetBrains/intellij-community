/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SwitchUtils {

  private SwitchUtils() {}

  /**
   * Calculates the number of branches in the specified switch statement.
   * When a default case is present the count will be returned as a negative number,
   * e.g. if a switch statement contains 4 labeled cases and a default case, it will return -5
   * @param statement  the statement to count the cases of.
   * @return a negative number if a default case was encountered.
   */
  public static int calculateBranchCount(@NotNull PsiSwitchStatement statement) {
    // preserved for plugin compatibility
    return calculateBranchCount((PsiSwitchBlock)statement);
  }

  /**
   * Calculates the number of branches in the specified switch block.
   * When a default case is present the count will be returned as a negative number,
   * e.g. if a switch block contains 4 labeled cases and a default case, it will return -5
   * @param block  the switch block to count the cases of.
   * @return a negative number if a default case was encountered.
   */
  public static int calculateBranchCount(@NotNull PsiSwitchBlock block) {
    final PsiCodeBlock body = block.getBody();
    if (body == null) {
      return 0;
    }
    int branches = 0;
    boolean defaultFound = false;
    for (final PsiSwitchLabelStatementBase child : PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class)) {
      if (child.isDefaultCase()) {
        defaultFound = true;
      }
      else {
        branches++;
      }
    }
    return defaultFound ? -branches - 1 : branches;
  }

  public static boolean canBeSwitchCase(PsiExpression expression, PsiExpression switchExpression, LanguageLevel languageLevel,
                                        Set<Object> existingCaseValues) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiExpression stringSwitchExpression = determinePossibleJdk17SwitchExpression(expression, existingCaseValues);
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, stringSwitchExpression)) {
        return true;
      }
    }
    final EqualityCheck check = EqualityCheck.from(expression);
    if (check != null) {
      final PsiExpression left = check.getLeft();
      final PsiExpression right = check.getRight();
      if (canBeCaseLabel(left, languageLevel, null)) {
        return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, right);
      }
      else if (canBeCaseLabel(right, languageLevel, null)) {
        return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, left);
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
        if (!canBeSwitchCase(operand, switchExpression, languageLevel, existingCaseValues)) {
          return false;
        }
      }
      return true;
    }
    else if (operation.equals(JavaTokenType.EQEQ) && operands.length == 2) {
      return (canBeCaseLabel(operands[0], languageLevel, existingCaseValues) &&
              EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operands[1])) ||
             (canBeCaseLabel(operands[1], languageLevel, existingCaseValues) &&
              EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operands[0]));
    }
    else {
      return false;
    }
  }

  /**
   * Returns true if given switch block has a rule-based format (like 'case 0 ->')
   * @param block block to test
   * @return true if given switch block has a rule-based format; false if it has conventional label-based format (like 'case 0:')
   * If switch body has no labels yet and language level permits, rule-based format is assumed.
   */
  public static boolean isRuleFormatSwitch(@NotNull PsiSwitchBlock block) {
    if (!HighlightingFeature.ENHANCED_SWITCH.isAvailable(block)) {
      return false;
    }
    final PsiSwitchLabelStatementBase label = PsiTreeUtil.getChildOfType(block.getBody(), PsiSwitchLabelStatementBase.class);
    return label == null || label instanceof PsiSwitchLabeledRuleStatement;
  }

  public static boolean canBeSwitchSelectorExpression(PsiExpression expression, LanguageLevel languageLevel) {
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    if (PsiType.CHAR.equals(type) || PsiType.BYTE.equals(type) || PsiType.SHORT.equals(type) || PsiType.INT.equals(type)) {
      return true;
    }
    else if (type instanceof PsiClassType && languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
      if (ExpressionUtils.isAnnotatedNullable(expression)) {
        return false;
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER) || type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
          type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) || type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)) {
        return true;
      }
      if (TypeConversionUtil.isEnumType(type)) {
        return true;
      }
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7) && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return true;
      }
    }
    return false;
  }

  @Contract("null -> null")
  @Nullable
  public static PsiExpression getSwitchSelectorExpression(PsiExpression expression) {
    if (expression == null) return null;
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
    final PsiExpression selectorExpression = getPossibleSwitchSelectorExpression(expression, languageLevel);
    return canBeSwitchSelectorExpression(selectorExpression, languageLevel) ? selectorExpression : null;
  }

  private static PsiExpression getPossibleSwitchSelectorExpression(PsiExpression expression, LanguageLevel languageLevel) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) {
      return null;
    }
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiExpression jdk17Expression = determinePossibleJdk17SwitchExpression(expression, null);
      if (jdk17Expression != null) {
        return jdk17Expression;
      }
    }
    final EqualityCheck check = EqualityCheck.from(expression);
    if (check != null) {
      final PsiExpression left = check.getLeft();
      final PsiExpression right = check.getRight();
      if (canBeCaseLabel(left, languageLevel, null)) {
        return right;
      }
      else if (canBeCaseLabel(right, languageLevel, null)) {
        return left;
      }
    }
    if (!(expression instanceof PsiPolyadicExpression)) {
      return null;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
    final IElementType operation = polyadicExpression.getOperationTokenType();
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operation.equals(JavaTokenType.OROR) && operands.length > 0) {
      return getPossibleSwitchSelectorExpression(operands[0], languageLevel);
    }
    else if (operation.equals(JavaTokenType.EQEQ) && operands.length == 2) {
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(operands[0]);
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(operands[1]);
      if (canBeCaseLabel(lhs, languageLevel, null)) {
        return rhs;
      }
      else if (canBeCaseLabel(rhs, languageLevel, null)) {
        return lhs;
      }
    }
    return null;
  }

  private static PsiExpression determinePossibleJdk17SwitchExpression(PsiExpression expression,
                                                                      Set<Object> existingCaseValues) {
    final EqualityCheck check = EqualityCheck.from(expression);
    if (check == null) {
      return null;
    }
    final PsiExpression left = check.getLeft();
    final PsiExpression right = check.getRight();
    final Object leftValue = ExpressionUtils.computeConstantExpression(left);
    if (leftValue!= null) {
      if (existingCaseValues == null || existingCaseValues.add(leftValue)) {
        return right;
      }
    }
    final Object rightValue = ExpressionUtils.computeConstantExpression(right);
    if (rightValue != null) {
      if (existingCaseValues == null || existingCaseValues.add(rightValue)) {
        return left;
      }
    }
    return null;
  }

  private static boolean canBeCaseLabel(PsiExpression expression, LanguageLevel languageLevel,
                                        Set<Object> existingCaseValues) {
    if (expression == null) {
      return false;
    }
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && expression instanceof PsiReferenceExpression) {
      final PsiElement referent = ((PsiReference)expression).resolve();
      if (referent instanceof PsiEnumConstant) {
        return existingCaseValues == null || existingCaseValues.add(referent);
      }
    }
    final PsiType type = expression.getType();
    if ((!languageLevel.isAtLeast(LanguageLevel.JDK_1_7) || !TypeUtils.isJavaLangString(type)) &&
        ((!PsiType.INT.equals(type) && !PsiType.SHORT.equals(type) && !PsiType.BYTE.equals(type) && !PsiType.CHAR.equals(type)))) {
      return false;
    }
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    if (value == null) {
      return false;
    }
    return existingCaseValues == null || existingCaseValues.add(value);
  }

  public static String findUniqueLabelName(PsiStatement statement, @NonNls String baseName) {
    final PsiElement ancestor = PsiTreeUtil.getParentOfType(statement, PsiMember.class);
    if (ancestor == null || !checkForLabel(baseName, ancestor)) {
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

  /**
   * @param label a switch label statement
   * @return list of enum constants which are targets of the specified label; empty list if the supplied element is not a switch label,
   * or it is not an enum switch.
   */
  @NotNull
  public static List<PsiEnumConstant> findEnumConstants(PsiSwitchLabelStatementBase label) {
    if (label == null) {
      return Collections.emptyList();
    }
    final PsiExpressionList list = label.getCaseValues();
    if (list == null) {
      return Collections.emptyList();
    }
    List<PsiEnumConstant> constants = new ArrayList<>();
    for (PsiExpression value : list.getExpressions()) {
      if (value instanceof PsiReferenceExpression) {
        final PsiElement target = ((PsiReferenceExpression)value).resolve();
        if (target instanceof PsiEnumConstant) {
          constants.add((PsiEnumConstant)target);
          continue;
        }
      }
      return Collections.emptyList();
    }
    return constants;
  }

  private static class LabelSearchVisitor extends JavaRecursiveElementWalkingVisitor {

    private final String m_labelName;
    private boolean m_used = false;

    LabelSearchVisitor(String name) {
      m_labelName = name;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
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

    private final Set<String> topLevelVariables = new HashSet<>(3);
    private final LinkedList<String> comments = new LinkedList<>();
    private final LinkedList<String> statementComments = new LinkedList<>();
    private final List<PsiExpression> caseExpressions = new ArrayList<>(3);
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
      return intersects(topLevelVariables, testBranch.topLevelVariables);
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
