/*
 * Copyright 2011-2017 Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.psiutils.SwitchUtils.IfStatementBranch;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class IfCanBeSwitchInspection extends BaseInspection {

  @NonNls private static final String ONLY_SAFE = "onlySuggestNullSafe";
  
  @SuppressWarnings("PublicField")
  public int minimumBranches = 3;

  @SuppressWarnings("PublicField")
  public boolean suggestIntSwitches = false;

  @SuppressWarnings("PublicField")
  public boolean suggestEnumSwitches = false;

  protected boolean onlySuggestNullSafe = true;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("if.can.be.switch.display.name");
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
    final JPanel panel = new JPanel(new GridBagLayout());
    final JLabel label = new JLabel(InspectionGadgetsBundle.message("if.can.be.switch.minimum.branch.option"));
    final NumberFormat formatter = NumberFormat.getIntegerInstance();
    formatter.setParseIntegerOnly(true);
    final JFormattedTextField valueField = new JFormattedTextField(formatter);
    valueField.setValue(Integer.valueOf(minimumBranches));
    valueField.setColumns(2);
    final Document document = valueField.getDocument();
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent e) {
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
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.insets.bottom = 4;
    constraints.weightx = 0.0;
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.NONE;
    constraints.insets.right = 10;
    panel.add(label, constraints);
    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.insets.right = 0;
    panel.add(valueField, constraints);
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 2;
    final CheckBox checkBox1 = new CheckBox(InspectionGadgetsBundle.message("if.can.be.switch.int.option"), this, "suggestIntSwitches");
    panel.add(checkBox1, constraints);
    constraints.gridy = 2;
    final CheckBox checkBox2 = new CheckBox(InspectionGadgetsBundle.message("if.can.be.switch.enum.option"), this, "suggestEnumSwitches");
    panel.add(checkBox2, constraints);
    constraints.gridy = 3;
    constraints.weighty = 1.0;
    final CheckBox checkBox3 =
      new CheckBox(InspectionGadgetsBundle.message("if.can.be.switch.null.safe.option"), this, "onlySuggestNullSafe");
    panel.add(checkBox3, constraints);
    return panel;
  }

  public void setOnlySuggestNullSafe(boolean onlySuggestNullSafe) {
    this.onlySuggestNullSafe = onlySuggestNullSafe;
  }

  private static class IfCanBeSwitchFix extends InspectionGadgetsFix {

    public IfCanBeSwitchFix() {}

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.can.be.switch.quickfix");
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
    String labelString = "";
    if (ControlFlowUtils.statementContainsNakedBreak(ifStatement)) {
      breakTarget = PsiTreeUtil.getParentOfType(ifStatement, PsiLoopStatement.class, PsiSwitchStatement.class);
      if (breakTarget != null) {
        final PsiElement parent = breakTarget.getParent();
        if (parent instanceof PsiLabeledStatement) {
          final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)parent;
          labelString = labeledStatement.getLabelIdentifier().getText();
          breakTarget = labeledStatement;
          breaksNeedRelabeled = true;
        }
        else {
          labelString = SwitchUtils.findUniqueLabelName(ifStatement, "label");
          breaksNeedRelabeled = true;
        }
      }
    }
    final PsiIfStatement statementToReplace = ifStatement;
    final PsiExpression switchExpression = SwitchUtils.getSwitchExpression(ifStatement, 0, false, true);
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
      dumpBranch(branch, castToInt, hasConflicts, breaksNeedRelabeled, labelString, switchStatementText);
    }
    switchStatementText.append('}');
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(ifStatement.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();
    if (breaksNeedRelabeled) {
      final StringBuilder out = new StringBuilder();
      if (!(breakTarget instanceof PsiLabeledStatement)) {
        out.append(labelString).append(':');
      }
      termReplace(breakTarget, statementToReplace, switchStatementText, out);
      final String newStatementText = out.toString();
      final PsiStatement newStatement = factory.createStatementFromText(newStatementText, ifStatement);
      breakTarget.replace(newStatement);
    }
    else {
      final PsiStatement newStatement = factory.createStatementFromText(switchStatementText.toString(), ifStatement);
      statementToReplace.replace(newStatement);
    }
  }

  @SafeVarargs
  @Nullable
  public static <T extends PsiElement> T getPrevSiblingOfType(@Nullable PsiElement element, @NotNull Class<T> aClass,
                                                              @NotNull Class<? extends PsiElement>... stopAt) {
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

  private static void termReplace(PsiElement target, PsiElement replace, StringBuilder stringToReplaceWith, StringBuilder out) {
    if (target.equals(replace)) {
      out.append(stringToReplaceWith);
    }
    else if (target.getChildren().length == 0) {
      out.append(target.getText());
    }
    else {
      final PsiElement[] children = target.getChildren();
      for (final PsiElement child : children) {
        termReplace(child, replace, stringToReplaceWith, out);
      }
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
      final boolean stringType = ExpressionUtils.hasStringType(qualifierExpression);
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, argument)) {
        branch.addCaseExpression(stringType? qualifierExpression : secondArgument);
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
    else {
      appendElement(bodyStatement, renameBreaks, breakLabelName, switchStatementText);
    }
    if (ControlFlowUtils.statementMayCompleteNormally(bodyStatement)) {
      switchStatementText.append("break;");
    }
    if (wrap) {
      switchStatementText.append('}');
    }
  }

  private static void appendElement(PsiElement element, boolean renameBreakElements, String breakLabelString,
                                    @NonNls StringBuilder switchStatementText) {
    final String text = element.getText();
    if (!renameBreakElements) {
      switchStatementText.append(text);
    }
    else if (element instanceof PsiBreakStatement) {
      final PsiBreakStatement breakStatement = (PsiBreakStatement)element;
      final PsiIdentifier identifier = breakStatement.getLabelIdentifier();
      if (identifier == null) {
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
    if (isEndOfLineComment(lastChild)) {
      switchStatementText.append('\n');
    }
  }

  private static boolean isEndOfLineComment(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    final PsiComment comment = (PsiComment)element;
    final IElementType tokenType = comment.getTokenType();
    return JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfCanBeSwitchVisitor();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (!onlySuggestNullSafe) {
      final Element e = new Element("option");
      e.setAttribute("name", ONLY_SAFE);
      e.setAttribute("value", Boolean.toString(onlySuggestNullSafe));
      node.addContent(e);
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element child : node.getChildren("option")) {
      if (Comparing.strEqual(child.getAttributeValue("name"), ONLY_SAFE)) {
        final String value = child.getAttributeValue("value");
        if (value != null) {
          onlySuggestNullSafe = Boolean.parseBoolean(value);
        }
        break;
      }
    }
  }

  private class IfCanBeSwitchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        return;
      }
      final PsiExpression switchExpression = SwitchUtils.getSwitchExpression(statement, minimumBranches, false, true);
      if (switchExpression == null) {
        return;
      }
      final ProblemHighlightType highlightType = shouldHighlight(switchExpression)
                                                 ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                                 : ProblemHighlightType.INFORMATION;
      registerError(statement.getFirstChild(), highlightType, switchExpression);
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
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        if (aClass == null) {
          return false;
        }
        if (!suggestEnumSwitches && aClass.isEnum()) {
          return false;
        }
        if (CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName())) {
          final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(switchExpression);
          if (parent instanceof PsiExpressionList && onlySuggestNullSafe && !ExpressionUtils.isAnnotatedNotNull(switchExpression)) {
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
