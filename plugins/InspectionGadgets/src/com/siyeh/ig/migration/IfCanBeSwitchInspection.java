// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.EnhancedSwitchMigrationInspection;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.DocumentAdapter;
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

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.text.NumberFormat;
import java.text.ParseException;
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
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    final JLabel label = new JLabel(InspectionGadgetsBundle.message("if.can.be.switch.minimum.branch.option"));
    final NumberFormat formatter = NumberFormat.getIntegerInstance();
    formatter.setParseIntegerOnly(true);
    final JFormattedTextField valueField = new JFormattedTextField(formatter);
    valueField.setValue(Integer.valueOf(minimumBranches));
    valueField.setColumns(2);
    final Document document = valueField.getDocument();
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent e) {
        try {
          valueField.commitEdit();
          minimumBranches =
            ((Number)valueField.getValue()).intValue();
        }
        catch (ParseException ignore) {
          // No luck this time
        }
      }
    });

    panel.addRow(label, valueField);
    panel.addCheckbox(InspectionGadgetsBundle.message("if.can.be.switch.int.option"), "suggestIntSwitches");
    panel.addCheckbox(InspectionGadgetsBundle.message("if.can.be.switch.enum.option"), "suggestEnumSwitches");
    panel.addCheckbox(InspectionGadgetsBundle.message("if.can.be.switch.null.safe.option"), "onlySuggestNullSafe");

    return panel;
  }

  public void setOnlySuggestNullSafe(boolean onlySuggestNullSafe) {
    this.onlySuggestNullSafe = onlySuggestNullSafe;
  }

  private static class IfCanBeSwitchFix extends InspectionGadgetsFix {

    IfCanBeSwitchFix() {}

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", PsiKeyword.IF, PsiKeyword.SWITCH);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement ifStatement = (PsiIfStatement)element;
      replaceIfWithSwitch(ifStatement);
    }
  }

  public static void replaceIfWithSwitch(PsiIfStatement ifStatement) {
    boolean breaksNeedRelabeled = false;
    PsiStatement breakTarget = null;
    String newLabel = "";
    if (ControlFlowUtils.statementContainsNakedBreak(ifStatement)) {
      breakTarget = PsiTreeUtil.getParentOfType(ifStatement, PsiLoopStatement.class, PsiSwitchStatement.class);
      if (breakTarget != null) {
        final PsiElement parent = breakTarget.getParent();
        if (parent instanceof PsiLabeledStatement) {
          final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)parent;
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
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
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
    else if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.OROR.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          extractCaseExpressions(operand, switchExpression, branch);
        }
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
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression contents = parenthesizedExpression.getExpression();
      extractCaseExpressions(contents, switchExpression, branch);
    }
  }

  private static void dumpBranch(IfStatementBranch branch, boolean castToInt, boolean wrap, boolean renameBreaks, String breakLabelName,
                                 @NonNls StringBuilder switchStatementText) {
    dumpComments(branch.getComments(), switchStatementText);
    if (branch.isElse()) {
      switchStatementText.append("default: ");
    }
    else {
      for (PsiExpression caseExpression : branch.getCaseExpressions()) {
        switchStatementText.append("case ").append(getCaseLabelText(caseExpression, castToInt)).append(": ");
      }
    }
    dumpComments(branch.getStatementComments(), switchStatementText);
    dumpBody(branch.getStatement(), wrap, renameBreaks, breakLabelName, switchStatementText);
  }

  @NonNls
  private static String getCaseLabelText(PsiExpression expression, boolean castToInt) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiEnumConstant) {
        final PsiEnumConstant enumConstant = (PsiEnumConstant)target;
        return enumConstant.getName();
      }
    }
    if (castToInt) {
      final PsiType type = expression.getType();
      if (!PsiType.INT.equals(type)) {
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
        switchStatementText.append("break ").append(breakLabelString).append(';');
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
    public void visitIfStatement(PsiIfStatement statement) {
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
      int branchCount = 0;
      final Set<Object> switchCaseValues = new HashSet<>();
      PsiIfStatement branch = statement;
      while (true) {
        branchCount++;
        if (!SwitchUtils.canBeSwitchCase(branch.getCondition(), switchExpression, languageLevel, switchCaseValues)) {
          return;
        }
        final PsiStatement elseBranch = branch.getElseBranch();
        if (!(elseBranch instanceof PsiIfStatement)) {
          break;
        }
        branch = (PsiIfStatement)elseBranch;
      }

      final ProblemHighlightType highlightType;
      if (shouldHighlight(switchExpression) && branchCount >= minimumBranches) {
        highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      else {
        if (!isOnTheFly()) return;
        highlightType = ProblemHighlightType.INFORMATION;
      }
      registerError(statement.getFirstChild(), highlightType);
    }

    private boolean shouldHighlight(PsiExpression switchExpression) {
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
        else if (PsiType.INT.equals(type) || PsiType.SHORT.equals(type) || PsiType.BYTE.equals(type) || PsiType.CHAR.equals(type)) {
          return false;
        }
      }
      if (type instanceof PsiClassType) {
        if (!suggestEnumSwitches && TypeConversionUtil.isEnumType(type)) {
          return false;
        }
        if (onlySuggestNullSafe && NullabilityUtil.getExpressionNullability(switchExpression, true) != Nullability.NOT_NULL) {
          final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(switchExpression);
          if (parent instanceof PsiExpressionList) {
            final PsiElement grandParent = parent.getParent();
            if (grandParent instanceof PsiMethodCallExpression) {
              final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
              if ("equals".equals(methodCallExpression.getMethodExpression().getReferenceName())) {
                // Objects.equals(switchExpression, other) or other.equals(switchExpression)
                return false;
              }
            }
          }
          return !(parent instanceof PsiPolyadicExpression); // == expression
        }
      }
      return !SideEffectChecker.mayHaveSideEffects(switchExpression);
    }
  }
}
