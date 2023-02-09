// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.EnhancedSwitchMigrationInspection;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.tree.java.PsiEmptyStatementImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.psiutils.SwitchUtils.IfStatementBranch;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IfCanBeSwitchInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public int minimumBranches = 3;

  @SuppressWarnings("PublicField")
  public boolean suggestIntSwitches = false;

  @SuppressWarnings("PublicField")
  public boolean suggestEnumSwitches = false;

  @SuppressWarnings("PublicField")
  public boolean onlySuggestNullSafe = true;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("if.can.be.switch.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IfCanBeSwitchFix();
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.number("minimumBranches", InspectionGadgetsBundle.message("if.can.be.switch.minimum.branch.option"), 1, 100),
      OptPane.checkbox("suggestIntSwitches", InspectionGadgetsBundle.message("if.can.be.switch.int.option")),
      OptPane.checkbox("suggestEnumSwitches", InspectionGadgetsBundle.message("if.can.be.switch.enum.option")),
      OptPane.checkbox("onlySuggestNullSafe", InspectionGadgetsBundle.message("if.can.be.switch.null.safe.option"))
    );
  }

  public void setOnlySuggestNullSafe(boolean onlySuggestNullSafe) {
    this.onlySuggestNullSafe = onlySuggestNullSafe;
  }

  @IntentionFamilyName
  public static @NotNull String getReplaceWithSwitchFixName(){
    return CommonQuickFixBundle.message("fix.replace.x.with.y", PsiKeyword.IF, PsiKeyword.SWITCH);
  }

  private static class IfCanBeSwitchFix extends InspectionGadgetsFix {

    IfCanBeSwitchFix() {}

    @Override
    @NotNull
    public String getFamilyName() {
      return getReplaceWithSwitchFixName();
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiIfStatement ifStatement)) {
        return;
      }
      if (HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(ifStatement)) {
        for (PsiIfStatement ifStatementInChain : getAllConditionalBranches(ifStatement)) {
          replaceCastsWithPatternVariable(ifStatementInChain);
        }
      }
      replaceIfWithSwitch(ifStatement);
    }
  }

  private static List<PsiIfStatement> getAllConditionalBranches(PsiIfStatement ifStatement){
    List<PsiIfStatement> ifStatements = new ArrayList<>();
    while (ifStatement != null) {
      ifStatements.add(ifStatement);
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch instanceof PsiIfStatement) {
        ifStatement = (PsiIfStatement) elseBranch;
      } else {
        ifStatement = null;
      }
    }
    return ifStatements;
  }

  private static void replaceCastsWithPatternVariable(PsiIfStatement ifStatement){
    PsiInstanceOfExpression targetInstanceOf =
      PsiTreeUtil.findChildOfType(ifStatement.getCondition(), PsiInstanceOfExpression.class, false);
    if (targetInstanceOf == null) return;
    if (targetInstanceOf.getPattern() != null) return;
    PsiTypeElement type = targetInstanceOf.getCheckType();
    if (type == null) return;

    List<PsiTypeCastExpression> relatedCastExpressions =
      SyntaxTraverser.psiTraverser(ifStatement.getThenBranch())
        .filter(PsiTypeCastExpression.class)
        .filter(cast -> InstanceOfUtils.findPatternCandidate(cast) == targetInstanceOf)
        .toList();

    PsiLocalVariable castedVariable = null;
    for (PsiTypeCastExpression castExpression : relatedCastExpressions) {
      castedVariable = findCastedLocalVariable(castExpression);
      if (castedVariable != null) break;
    }

    String name = castedVariable != null
                  ? castedVariable.getName()
                  : new VariableNameGenerator(targetInstanceOf, VariableKind.LOCAL_VARIABLE).byType(type.getType()).generate(true);

    CommentTracker ct = new CommentTracker();
    for (PsiTypeCastExpression castExpression : relatedCastExpressions) {
      ct.replace(skipParenthesizedExprUp(castExpression), name);
    }
    if (castedVariable != null) {
      ct.delete(castedVariable);
    }
    ct.replaceExpressionAndRestoreComments(
      targetInstanceOf,
      ct.text(targetInstanceOf.getOperand()) + " instanceof " + ct.text(type) + " " + name
    );
  }

  private static @Nullable PsiLocalVariable findCastedLocalVariable(PsiTypeCastExpression castExpression) {
    PsiLocalVariable variable = PsiTreeUtil.getParentOfType(castExpression, PsiLocalVariable.class);
    if (variable == null) return null;
    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
    if (initializer != castExpression) return null;
    PsiElement scope = PsiUtil.getVariableCodeBlock(variable, null);
    if (scope == null) return null;
    if (!HighlightControlFlowUtil.isEffectivelyFinal(variable, scope, null)) return null;
    return variable;
  }

  private static PsiElement skipParenthesizedExprUp(@NotNull PsiElement expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = expression.getParent();
    }
    return expression;
  }

  public static void replaceIfWithSwitch(PsiIfStatement ifStatement) {
    boolean breaksNeedRelabeled = false;
    PsiStatement breakTarget = null;
    String newLabel = "";
    if (ControlFlowUtils.statementContainsNakedBreak(ifStatement)) {
      breakTarget = PsiTreeUtil.getParentOfType(ifStatement, PsiLoopStatement.class, PsiSwitchStatement.class);
      if (breakTarget != null) {
        final PsiElement parent = breakTarget.getParent();
        if (parent instanceof PsiLabeledStatement labeledStatement) {
          newLabel = labeledStatement.getLabelIdentifier().getText();
          breakTarget = labeledStatement;
        }
        else {
          newLabel = SwitchUtils.findUniqueLabelName(ifStatement, "label");
        }
        breaksNeedRelabeled = true;
      }
    }
    final PsiIfStatement statementToReplace = ifStatement;
    final PsiExpression switchExpression = SwitchUtils.getSwitchSelectorExpression(ifStatement.getCondition());
    if (switchExpression == null) {
      return;
    }
    final List<IfStatementBranch> branches = new ArrayList<>(20);
    while (true) {
      final PsiExpression condition = ifStatement.getCondition();
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final IfStatementBranch ifBranch = new IfStatementBranch(thenBranch, false);
      extractCaseExpressions(condition, switchExpression, ifBranch);
      if (!branches.isEmpty()) {
        extractIfComments(ifStatement, ifBranch);
      }
      extractStatementComments(thenBranch, ifBranch);
      branches.add(ifBranch);
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch instanceof PsiIfStatement) {
        ifStatement = (PsiIfStatement)elseBranch;
      }
      else if (elseBranch == null) {
        break;
      }
      else {
        final IfStatementBranch elseIfBranch = new IfStatementBranch(elseBranch, true);
        final PsiKeyword elseKeyword = ifStatement.getElseElement();
        extractIfComments(elseKeyword, elseIfBranch);
        extractStatementComments(elseBranch, elseIfBranch);
        branches.add(elseIfBranch);
        break;
      }
    }

    if (SwitchUtils.canBePatternSwitchCase(ifStatement.getCondition(), switchExpression)) {
      final boolean hasDefaultElse = ContainerUtil.exists(branches, (branch) -> branch.isElse());
      if (!hasDefaultElse && !hasUnconditionalPatternCheck(ifStatement, switchExpression)) {
        branches.add(new IfStatementBranch(new PsiEmptyStatementImpl(), true));
      }
    }
    if (HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(switchExpression)){
      if (getNullability(switchExpression) != Nullability.NOT_NULL && findNullCheckedOperand(statementToReplace) == null) {
        final IfStatementBranch defaultBranch = ContainerUtil.find(branches, (branch) -> branch.isElse());
        final PsiElementFactory factory = PsiElementFactory.getInstance(ifStatement.getProject());
        final PsiExpression condition = factory.createExpressionFromText("null", switchExpression.getContext());
        if (defaultBranch != null) defaultBranch.addCaseExpression(condition);
      }
    }

    @NonNls final StringBuilder switchStatementText = new StringBuilder();
    switchStatementText.append("switch(").append(switchExpression.getText()).append("){");
    final PsiType type = switchExpression.getType();
    final boolean castToInt = type != null && type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER);
    for (IfStatementBranch branch : branches) {
      boolean hasConflicts = false;
      for (IfStatementBranch testBranch : branches) {
        if (branch == testBranch) {
          continue;
        }
        if (branch.topLevelDeclarationsConflictWith(testBranch)) {
          hasConflicts = true;
        }
      }
      dumpBranch(branch, castToInt, hasConflicts, breaksNeedRelabeled, newLabel, switchStatementText);
    }
    switchStatementText.append('}');
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(ifStatement.getProject());
    final PsiStatement newStatement = factory.createStatementFromText(switchStatementText.toString(), ifStatement);
    final PsiSwitchStatement replacement = (PsiSwitchStatement)statementToReplace.replace(newStatement);
    if (HighlightingFeature.ENHANCED_SWITCH.isAvailable(replacement)) {
      final EnhancedSwitchMigrationInspection.SwitchReplacer replacer = EnhancedSwitchMigrationInspection.findSwitchReplacer(replacement);
      if (replacer != null) {
        replacer.replace(replacement);
      }
    }
    if (breaksNeedRelabeled) {
      final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)factory.createStatementFromText(newLabel + ":;", null);
      final PsiStatement statement = labeledStatement.getStatement();
      assert statement != null;
      statement.replace(breakTarget);
      breakTarget.replace(labeledStatement);
    }
  }

  @SafeVarargs
  @Nullable
  public static <T extends PsiElement> T getPrevSiblingOfType(@Nullable PsiElement element, @NotNull Class<T> aClass,
                                                              Class<? extends PsiElement> @NotNull ... stopAt) {
    if (element == null) {
      return null;
    }
    PsiElement sibling = element.getPrevSibling();
    while (sibling != null && !aClass.isInstance(sibling)) {
      for (Class<? extends PsiElement> stopClass : stopAt) {
        if (stopClass.isInstance(sibling)) {
          return null;
        }
      }
      sibling = sibling.getPrevSibling();
    }
    //noinspection unchecked
    return (T)sibling;
  }

  private static void extractIfComments(PsiElement element, IfStatementBranch out) {
    PsiComment comment = getPrevSiblingOfType(element, PsiComment.class, PsiStatement.class);
    while (comment != null) {
      out.addComment(getCommentText(comment));
      comment = getPrevSiblingOfType(comment, PsiComment.class, PsiStatement.class);
    }
  }

  private static void extractStatementComments(PsiElement element, IfStatementBranch out) {
    PsiComment comment = getPrevSiblingOfType(element, PsiComment.class, PsiStatement.class, PsiKeyword.class);
    while (comment != null) {
      out.addStatementComment(getCommentText(comment));
      comment = getPrevSiblingOfType(comment, PsiComment.class, PsiStatement.class, PsiKeyword.class);
    }
  }

  private static String getCommentText(PsiComment comment) {
    final PsiElement sibling = comment.getPrevSibling();
    if (sibling instanceof PsiWhiteSpace) {
      final String whiteSpaceText = sibling.getText();
      return whiteSpaceText.startsWith("\n") ? whiteSpaceText.substring(1) + comment.getText() : comment.getText();
    }
    else {
      return comment.getText();
    }
  }

  private static void extractCaseExpressions(PsiExpression expression, PsiExpression switchExpression, IfStatementBranch branch) {
    if (expression instanceof PsiMethodCallExpression methodCallExpression) {
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression argument = arguments[0];
      final PsiExpression secondArgument = arguments.length > 1 ? arguments[1] : null;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, argument)) {
        branch.addCaseExpression(secondArgument == null ? qualifierExpression : secondArgument);
      }
      else {
        branch.addCaseExpression(argument);
      }
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      branch.addCaseExpression(expression);
    }
    else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.OROR.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          extractCaseExpressions(operand, switchExpression, branch);
        }
      } else if (JavaTokenType.ANDAND.equals(tokenType)) {
        branch.addCaseExpression(polyadicExpression);
      }
      else if (operands.length == 2) {
        final PsiExpression lhs = operands[0];
        final PsiExpression rhs = operands[1];
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, rhs)) {
          branch.addCaseExpression(lhs);
        }
        else {
          branch.addCaseExpression(rhs);
        }
      }
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      final PsiExpression contents = parenthesizedExpression.getExpression();
      extractCaseExpressions(contents, switchExpression, branch);
    }
  }

  private static void dumpBranch(IfStatementBranch branch, boolean castToInt, boolean wrap, boolean renameBreaks, String breakLabelName,
                                 @NonNls StringBuilder switchStatementText) {
    dumpComments(branch.getComments(), switchStatementText);
    for (PsiExpression caseExpression : branch.getCaseExpressions()) {
      switchStatementText.append("case ").append(getCaseLabelText(caseExpression, castToInt)).append(": ");
    }
    if (branch.isElse()) {
      switchStatementText.append("default: ");
    }
    dumpComments(branch.getStatementComments(), switchStatementText);
    dumpBody(branch.getStatement(), wrap, renameBreaks, breakLabelName, switchStatementText);
  }

  @NonNls
  private static String getCaseLabelText(PsiExpression expression, boolean castToInt) {
    if (expression instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiEnumConstant enumConstant) {
        return enumConstant.getName();
      }
    }
    final String patternCaseText = SwitchUtils.createPatternCaseText(expression);
    if (patternCaseText != null) return patternCaseText;
    if (castToInt) {
      final PsiType type = expression.getType();
      if (!PsiTypes.intType().equals(type)) {
        /*
        because
        Integer a = 1;
        switch (a) {
            case (byte)7:
        }
        does not compile with javac (but does with Eclipse)
        */
        return "(int)" + expression.getText();
      }
    }
    return expression.getText();
  }

  private static void dumpComments(List<String> comments, StringBuilder switchStatementText) {
    if (comments.isEmpty()) {
      return;
    }
    switchStatementText.append('\n');
    for (String comment : comments) {
      switchStatementText.append(comment).append('\n');
    }
  }

  private static void dumpBody(PsiStatement bodyStatement, boolean wrap, boolean renameBreaks, String breakLabelName,
                               @NonNls StringBuilder switchStatementText) {
    if (wrap) {
      switchStatementText.append('{');
    }
    if (bodyStatement instanceof PsiBlockStatement) {
      final PsiCodeBlock codeBlock = ((PsiBlockStatement)bodyStatement).getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      //skip the first and last members, to unwrap the block
      for (int i = 1; i < children.length - 1; i++) {
        final PsiElement child = children[i];
        appendElement(child, renameBreaks, breakLabelName, switchStatementText);
      }
    }
    else if (bodyStatement != null) {
      appendElement(bodyStatement, renameBreaks, breakLabelName, switchStatementText);
    }
    if (ControlFlowUtils.statementMayCompleteNormally(bodyStatement)) {
      switchStatementText.append("break;");
    }
    if (wrap) {
      switchStatementText.append('}');
    }
  }

  private static void appendElement(PsiElement element, boolean renameBreakElements, String breakLabelString, @NonNls StringBuilder switchStatementText) {
    final String text = element.getText();
    if (!renameBreakElements) {
      switchStatementText.append(text);
    }
    else if (element instanceof PsiBreakStatement) {
      final PsiIdentifier labelIdentifier = ((PsiBreakStatement)element).getLabelIdentifier();
      if (labelIdentifier == null) {
        PsiElement child = element.getFirstChild();
        switchStatementText.append(child.getText()).append(" ").append(breakLabelString);
        child = child.getNextSibling();
        while (child != null) {
          switchStatementText.append(child.getText());
          child = child.getNextSibling();
        }
        return;
      }
      else {
        switchStatementText.append(text);
      }
    }
    else if (element instanceof PsiBlockStatement || element instanceof PsiCodeBlock || element instanceof PsiIfStatement) {
      final PsiElement[] children = element.getChildren();
      for (final PsiElement child : children) {
        appendElement(child, true, breakLabelString, switchStatementText);
      }
    }
    else {
      switchStatementText.append(text);
    }
    final PsiElement lastChild = element.getLastChild();
    if (lastChild instanceof PsiComment && ((PsiComment)lastChild).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
      switchStatementText.append('\n');
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfCanBeSwitchVisitor();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    defaultWriteSettings(node, "onlySuggestNullSafe");
    writeBooleanOption(node, "onlySuggestNullSafe", true);
  }

  private class IfCanBeSwitchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(statement);
      final PsiExpression switchExpression = SwitchUtils.getSwitchSelectorExpression(condition);
      if (switchExpression == null) {
        return;
      }
      boolean isPatternMatch = SwitchUtils.canBePatternSwitchCase(condition, switchExpression);
      int branchCount = 0;
      final Set<Object> switchCaseValues = new HashSet<>();
      PsiIfStatement branch = statement;
      while (true) {
        branchCount++;
        if (!SwitchUtils.canBeSwitchCase(branch.getCondition(), switchExpression, languageLevel, switchCaseValues, isPatternMatch)) {
          return;
        }
        final PsiStatement elseBranch = branch.getElseBranch();
        if (!(elseBranch instanceof PsiIfStatement)) {
          break;
        }
        branch = (PsiIfStatement)elseBranch;
      }

      final ProblemHighlightType highlightType;
      if (shouldHighlight(statement, switchExpression) && branchCount >= minimumBranches) {
        highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      else {
        if (!isOnTheFly()) return;
        highlightType = ProblemHighlightType.INFORMATION;
      }
      registerError(statement.getFirstChild(), highlightType);
    }

    private boolean shouldHighlight(PsiIfStatement ifStatement, PsiExpression switchExpression) {
      final PsiType type = switchExpression.getType();
      if (!suggestIntSwitches) {
        if (type instanceof PsiClassType) {
          if (type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER)) {
            return false;
          }
        }
        else if (PsiTypes.intType().equals(type) || PsiTypes.shortType().equals(type) || PsiTypes.byteType().equals(type) || PsiTypes.charType()
          .equals(type)) {
          return false;
        }
      }
      if (type instanceof PsiClassType) {
        if (!suggestEnumSwitches && TypeConversionUtil.isEnumType(type)) {
          return false;
        }
        Nullability nullability = getNullability(switchExpression);
        if (HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(switchExpression) && !ClassUtils.isPrimitive(switchExpression.getType())) {
          if (hasDefaultElse(ifStatement) || findNullCheckedOperand(ifStatement) != null || hasUnconditionalPatternCheck(ifStatement, switchExpression)) {
            nullability = Nullability.NOT_NULL;
          }
        }
        if (nullability == Nullability.NULLABLE) {
          return false;
        }
        if (onlySuggestNullSafe && nullability != Nullability.NOT_NULL) {
          return false;
        }
      }
      return !SideEffectChecker.mayHaveSideEffects(switchExpression);
    }
  }

  private static boolean hasUnconditionalPatternCheck(PsiIfStatement ifStatement, PsiExpression switchExpression){
    final PsiType type = switchExpression.getType();
    if (type == null) return false;

    PsiIfStatement currentIfInChain = ifStatement;
    while (currentIfInChain != null) {
      final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(currentIfInChain.getCondition());
      if (condition instanceof PsiPolyadicExpression polyadicExpression) {
        if (JavaTokenType.OROR.equals(polyadicExpression.getOperationTokenType())) {
          if (ContainerUtil.exists(polyadicExpression.getOperands(), (operand) -> hasUnconditionalPatternCheck(type, operand))) {
            return true;
          }
        }
      }
      if (hasUnconditionalPatternCheck(type, condition)) {
        return true;
      }
      final PsiStatement elseBranch = currentIfInChain.getElseBranch();
      if (elseBranch instanceof PsiIfStatement) {
        currentIfInChain = (PsiIfStatement)elseBranch;
      } else {
        currentIfInChain = null;
      }
    }

    return false;
  }

  private static boolean hasUnconditionalPatternCheck(PsiType type, PsiExpression check) {
    final PsiCaseLabelElement pattern = SwitchUtils.createPatternFromExpression(check);
    if (pattern == null) return false;
    return JavaPsiPatternUtil.isUnconditionalForType(pattern, type);
  }

  private static Nullability getNullability(PsiExpression expression) {
    // expression.equals("string") -> expression == NOT_NULL
    if (ExpressionUtils.getCallForQualifier(expression) != null) {
      return Nullability.NOT_NULL;
    }
    // inferred nullability
    Nullability normal = NullabilityUtil.getExpressionNullability(expression, false);
    Nullability dataflow = NullabilityUtil.getExpressionNullability(expression, true);
    if (normal == Nullability.NOT_NULL || dataflow == Nullability.NOT_NULL) {
      return Nullability.NOT_NULL;
    }
    if (normal == Nullability.NULLABLE || dataflow == Nullability.NULLABLE) {
      return Nullability.NULLABLE;
    }
    return Nullability.UNKNOWN;
  }

  private static PsiExpression findNullCheckedOperand(PsiIfStatement ifStatement) {
    final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    if (condition instanceof PsiPolyadicExpression polyadicExpression) {
      if (JavaTokenType.OROR.equals(polyadicExpression.getOperationTokenType())) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          final PsiExpression nullCheckedExpression = SwitchUtils.findNullCheckedOperand(PsiUtil.skipParenthesizedExprDown(operand));
          if (nullCheckedExpression != null) return nullCheckedExpression;
        }
      }
    }
    final PsiExpression nullCheckedOperand = SwitchUtils.findNullCheckedOperand(condition);
    if (nullCheckedOperand != null) return nullCheckedOperand;
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch instanceof PsiIfStatement) {
      return findNullCheckedOperand((PsiIfStatement)elseBranch);
    } else {
      return null;
    }
  }

  private static boolean hasDefaultElse(PsiIfStatement ifStatement) {
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch == null) return false;
    if (elseBranch instanceof PsiIfStatement) {
      return hasDefaultElse((PsiIfStatement)elseBranch);
    } else {
      return true;
    }
  }
}
