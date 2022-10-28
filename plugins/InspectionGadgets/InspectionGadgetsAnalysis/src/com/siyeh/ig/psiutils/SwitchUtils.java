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
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ipp.psiutils.ErrorUtil;
import one.util.streamex.StreamEx;
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
    List<PsiElement> switchBranches = getSwitchBranches(block);
    if (switchBranches.isEmpty()) return 0;
    int branches = 0;
    boolean defaultFound = false;
    for (PsiElement branch : switchBranches) {
      if (branch instanceof PsiSwitchLabelStatementBase) {
        if (((PsiSwitchLabelStatementBase)branch).isDefaultCase()) {
          defaultFound = true;
        }
      }
      else if (branch instanceof PsiCaseLabelElement) {
        if (branch instanceof PsiDefaultCaseLabelElement) {
          defaultFound = true;
        }
        else {
          branches++;
        }
      }
    }
    final PsiCodeBlock body = block.getBody();
    if (body == null) {
      return 0;
    }
    return defaultFound ? -branches - 1 : branches;
  }

  /**
   * @param block the switch block
   * @return a list of switch branches consisting of either {@link PsiSwitchLabelStatementBase} or {@link PsiCaseLabelElement}
   */
  @NotNull
  public static List<PsiElement> getSwitchBranches(@NotNull PsiSwitchBlock block) {
    final PsiCodeBlock body = block.getBody();
    if (body == null) return Collections.emptyList();
    List<PsiElement> result = new SmartList<>();
    for (PsiSwitchLabelStatementBase child : PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class)) {
      if (child.isDefaultCase()) {
        result.add(child);
      }
      else {
        PsiCaseLabelElementList labelElementList = child.getCaseLabelElementList();
        if (labelElementList == null) continue;
        Collections.addAll(result, labelElementList.getElements());
      }
    }
    return result;
  }

  public static boolean canBeSwitchCase(PsiExpression expression, PsiExpression switchExpression, LanguageLevel languageLevel,
                                        Set<Object> existingCaseValues, boolean isPatternMatch) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (isPatternMatch) {
      if (canBePatternSwitchCase(expression, switchExpression)) {
        final PsiPattern pattern = createPatternFromExpression(expression);
        if (pattern == null) return true;
        for (Object caseValue : existingCaseValues) {
          if (caseValue instanceof PsiPattern && JavaPsiPatternUtil.dominates((PsiPattern) caseValue, pattern)) {
            return false;
          }
        }
        existingCaseValues.add(pattern);
        return true;
      } else {
        return false;
      }
    }
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
    if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
      return false;
    }
    final IElementType operation = polyadicExpression.getOperationTokenType();
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operation.equals(JavaTokenType.OROR)) {
      for (PsiExpression operand : operands) {
        if (!canBeSwitchCase(operand, switchExpression, languageLevel, existingCaseValues, false)) {
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

  public static @Nullable PsiPattern createPatternFromExpression(@NotNull PsiExpression expression) {
    PsiElementFactory factory = PsiElementFactory.getInstance(expression.getProject());
    final String patternCaseText = createPatternCaseText(expression);
    if (patternCaseText == null) return null;
    final String switchText = "switch(o) { case " + patternCaseText + ": break; }";
    final PsiElement switchStatement = factory.createStatementFromText(switchText, expression.getContext());
    return PsiTreeUtil.findChildOfType(switchStatement, PsiPattern.class);
  }

  /**
   * Returns true if given switch block has a rule-based format (like 'case 0 ->')
   * @param block a switch block to test
   * @return true if given switch block has a rule-based format; false if it has conventional label-based format (like 'case 0:')
   * If switch body has no labels yet and language level permits the rule-based format is assumed.
   */
  @Contract(pure = true)
  public static boolean isRuleFormatSwitch(@NotNull PsiSwitchBlock block) {
    if (!HighlightingFeature.ENHANCED_SWITCH.isAvailable(block)) {
      return false;
    }

    final PsiCodeBlock switchBody = block.getBody();
    if (switchBody != null) {
      for (var child = switchBody.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiSwitchLabelStatementBase && !isBeingCompleted((PsiSwitchLabelStatementBase)child)) {
          return child instanceof PsiSwitchLabeledRuleStatement;
        }
      }
    }

    return true;
  }

  /**
   * Checks if the label is being completed and there are no other case label elements in the list of the case label's elements
   * @param label the label to analyze
   * @return true if the label is currently being completed
   */
  @Contract(pure = true)
  private static boolean isBeingCompleted(@NotNull PsiSwitchLabelStatementBase label) {
    if (!(label.getLastChild() instanceof PsiErrorElement)) return false;

    final PsiCaseLabelElementList list = label.getCaseLabelElementList();
    return list != null && list.getElements().length == 1;
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
      return HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(expression);
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
    if (HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(expression)) {
      final PsiExpression patternSwitchExpression = findPatternSwitchExpression(expression);
      if (patternSwitchExpression != null) return patternSwitchExpression;
    }
    if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
      return null;
    }
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

  private static @Nullable PsiExpression findPossiblePatternOperand(@Nullable PsiExpression expression) {
    if (expression instanceof PsiInstanceOfExpression) {
      return ((PsiInstanceOfExpression)expression).getOperand();
    }
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final IElementType operationToken = polyadicExpression.getOperationTokenType();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (JavaTokenType.ANDAND.equals(operationToken)) {
        final PsiExpression patternOperand = findPossiblePatternOperand(operands[0]);
        if (patternOperand != null) return patternOperand;
        for (PsiExpression operand : operands) {
          final PsiExpression pattern = findPossiblePatternOperand(operand);
          if (pattern != null) return pattern;
          if (SideEffectChecker.mayHaveSideEffects(operand)) break;
        }
      }
    }
    return null;
  }

  public static @Nullable PsiExpression findPatternSwitchExpression(@Nullable PsiExpression expression){
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    final PsiExpression patternOperand = findPossiblePatternOperand(expression);
    if (patternOperand != null) return patternOperand;
    final PsiExpression nullCheckedOperand = findNullCheckedOperand(expression);
    if (nullCheckedOperand != null) return nullCheckedOperand;
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final IElementType operationToken = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.OROR.equals(operationToken)) {
        final PsiExpression[] operands = polyadicExpression.getOperands();
        if (operands.length == 2) {
          PsiExpression firstOperand = findNullCheckedOperand(operands[0]);
          PsiExpression secondOperand = findPossiblePatternOperand(operands[1]);
          if (firstOperand == null || secondOperand == null) {
            firstOperand = findPossiblePatternOperand(operands[0]);
            secondOperand = findNullCheckedOperand(operands[1]);
          }
          if (firstOperand == null || secondOperand == null) {
            return null;
          }
          if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(firstOperand, secondOperand)){
            return firstOperand;
          }
        }
      }
    }
    return null;
  }

  @Contract("null, _ -> false")
  public static boolean canBePatternSwitchCase(@Nullable PsiExpression expression, @NotNull PsiExpression switchExpression) {
    if (expression == null) return false;
    final PsiExpression localSwitchExpression = findPatternSwitchExpression(expression);
    return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(localSwitchExpression, switchExpression);
  }

  public static @Nullable PsiExpression findNullCheckedOperand(PsiExpression expression){
    if (!(expression instanceof PsiBinaryExpression binaryExpression)) return null;
    if (! JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType())) return null;
    if (ExpressionUtils.isNullLiteral(binaryExpression.getLOperand())) {
      return binaryExpression.getROperand();
    } else if(ExpressionUtils.isNullLiteral(binaryExpression.getROperand())) {
      return binaryExpression.getLOperand();
    } else {
      return null;
    }
  }

  /**
   * @param switchBlock a switch statement or expression
   * @return either default switch label statement {@link PsiSwitchLabelStatementBase}, or {@link PsiDefaultCaseLabelElement},
   * or null, if nothing was found.
   */
  @Nullable
  public static PsiElement findDefaultElement(@NotNull PsiSwitchBlock switchBlock) {
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return null;
    for (PsiStatement statement : body.getStatements()) {
      PsiSwitchLabelStatementBase switchLabelStatement = ObjectUtils.tryCast(statement, PsiSwitchLabelStatementBase.class);
      if (switchLabelStatement == null) continue;
      PsiElement defaultElement = findDefaultElement(switchLabelStatement);
      if (defaultElement != null) return defaultElement;
    }
    return null;
  }

  /**
   * @param label a switch label statement
   * @return either default switch label statement {@link PsiSwitchLabelStatementBase}, or {@link PsiDefaultCaseLabelElement},
   * or null, if nothing was found.
   */
  @Nullable
  public static PsiElement findDefaultElement(@NotNull PsiSwitchLabelStatementBase label) {
    if (label.isDefaultCase()) return label;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList == null) return null;
    return ContainerUtil.find(labelElementList.getElements(),
                              labelElement -> labelElement instanceof PsiDefaultCaseLabelElement);
  }

  public static @Nullable @NonNls String createPatternCaseText(PsiExpression expression){
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiInstanceOfExpression instanceOf) {
      final PsiPrimaryPattern pattern = instanceOf.getPattern();
      if (pattern != null) {
        if (pattern instanceof PsiDeconstructionPattern deconstruction && ErrorUtil.containsError(deconstruction.getDeconstructionList())) {
          return null;
        }
        return pattern.getText();
      }
      final PsiTypeElement typeElement = instanceOf.getCheckType();
      final PsiType type = typeElement != null ? typeElement.getType() : null;
      String name = new VariableNameGenerator(instanceOf, VariableKind.LOCAL_VARIABLE).byType(type).generate(true);
      String typeText = typeElement != null ? typeElement.getText() : CommonClassNames.JAVA_LANG_OBJECT;
      return typeText + " " + name;
    }
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final IElementType operationToken = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.ANDAND.equals(operationToken)){
        final PsiExpression[] operands = polyadicExpression.getOperands();
        final PsiExpression instanceOf = ContainerUtil.find(operands, operand -> operand instanceof PsiInstanceOfExpression);
        StringBuilder builder = new StringBuilder();
        builder.append(createPatternCaseText(instanceOf));
        boolean needAppendWhen = HighlightingFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isAvailable(expression);
        for (PsiExpression operand : operands) {
          if (operand != instanceOf) {
            builder.append(needAppendWhen ? " when " : " && ").append(operand.getText());
            needAppendWhen = false;
          }
        }
        return builder.toString();
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
        !PsiType.INT.equals(type) && !PsiType.SHORT.equals(type) && !PsiType.BYTE.equals(type) && !PsiType.CHAR.equals(type)) {
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
    final PsiCaseLabelElementList list = label.getCaseLabelElementList();
    if (list == null) {
      return Collections.emptyList();
    }
    List<PsiEnumConstant> constants = new ArrayList<>();
    for (PsiCaseLabelElement labelElement : list.getElements()) {
      if (labelElement instanceof PsiDefaultCaseLabelElement ||
          ExpressionUtils.isNullLiteral(ObjectUtils.tryCast(labelElement, PsiExpression.class))) {
        continue;
      }
      if (labelElement instanceof PsiReferenceExpression) {
        final PsiElement target = ((PsiReferenceExpression)labelElement).resolve();
        if (target instanceof PsiEnumConstant) {
          constants.add((PsiEnumConstant)target);
          continue;
        }
      }
      return Collections.emptyList();
    }
    return constants;
  }

  /**
   * Checks if the given switch label statement contains a {@code default} case
   *
   * @param label a switch label statement to test
   * @return {@code true} if the given switch label statement contains a {@code default} case, {@code false} otherwise
   */
  public static boolean isDefaultLabel(@Nullable PsiSwitchLabelStatementBase label) {
    if (label == null) return false;
    if (label.isDefaultCase()) return true;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList == null) return false;
    return ContainerUtil.exists(labelElementList.getElements(), element -> element instanceof PsiDefaultCaseLabelElement);
  }

  /**
   * Checks if the given switch label statement contains only a {@code default} case and nothing else
   *
   * @param label a switch label statement to test
   * @return {@code true} if the given switch label statement contains only a {@code default} case and nothing else,
   * {@code false} otherwise.
   */
  public static boolean hasOnlyDefaultCase(@Nullable PsiSwitchLabelStatementBase label) {
    if (label == null) return false;
    if (label.isDefaultCase()) return true;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    return labelElementList != null &&
           labelElementList.getElementCount() == 1 &&
           labelElementList.getElements()[0] instanceof PsiDefaultCaseLabelElement;
  }

  /**
   * Checks if the given switch label statement contains a {@code default} case or a total pattern
   *
   * @param label a switch label statement to test
   * @return {@code true} if the given switch label statement contains a {@code default} case or a total pattern,
   * {@code false} otherwise.
   */
  public static boolean isTotalLabel(@Nullable PsiSwitchLabelStatementBase label) {
    if (label == null) return false;
    if (isDefaultLabel(label)) return true;
    PsiSwitchBlock switchBlock = label.getEnclosingSwitchBlock();
    if (switchBlock == null) return false;
    PsiExpression expression = switchBlock.getExpression();
    if (expression == null) return false;
    PsiType type = expression.getType();
    if (type == null) return false;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList == null) return false;
    return StreamEx.of(labelElementList.getElements()).select(PsiPattern.class)
      .anyMatch(pattern -> JavaPsiPatternUtil.isTotalForType(pattern, type));
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
    public void visitLabeledStatement(@NotNull PsiLabeledStatement statement) {
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
      if (statement instanceof PsiDeclarationStatement declarationStatement) {
        final PsiElement[] elements = declarationStatement.getDeclaredElements();
        for (PsiElement element : elements) {
          final PsiVariable variable = (PsiVariable)element;
          final String varName = variable.getName();
          topLevelVariables.add(varName);
        }
      }
      else if (statement instanceof PsiBlockStatement block) {
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement1 : statements) {
          calculateVariablesDeclared(statement1);
        }
      }
    }
  }
}
