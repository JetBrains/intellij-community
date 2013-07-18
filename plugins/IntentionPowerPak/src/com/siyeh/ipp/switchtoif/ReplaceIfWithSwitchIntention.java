/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.switchtoif;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.SwitchUtils.IfStatementBranch;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ReplaceIfWithSwitchIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new IfToSwitchPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken switchToken = (PsiJavaToken)element;
    PsiIfStatement ifStatement = (PsiIfStatement)switchToken.getParent();
    if (ifStatement == null) {
      return;
    }
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
    final PsiExpression switchExpression = SwitchUtils.getSwitchExpression(ifStatement, 0);
    if (switchExpression == null) {
      return;
    }
    final List<IfStatementBranch> branches = new ArrayList<IfStatementBranch>(20);
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
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(element.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();
    if (breaksNeedRelabeled) {
      final StringBuilder out = new StringBuilder();
      if (!(breakTarget instanceof PsiLabeledStatement)) {
        out.append(labelString).append(':');
      }
      termReplace(breakTarget, statementToReplace, switchStatementText, out);
      final String newStatementText = out.toString();
      final PsiStatement newStatement = factory.createStatementFromText(newStatementText, element);
      breakTarget.replace(newStatement);
    }
    else {
      final PsiStatement newStatement = factory.createStatementFromText(switchStatementText.toString(), element);
      statementToReplace.replace(newStatement);
    }
  }

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
    return (T)sibling;
  }

  private static void extractIfComments(PsiElement element, IfStatementBranch out) {
    PsiComment comment = getPrevSiblingOfType(element, PsiComment.class, PsiStatement.class);
    while (comment != null) {
      final PsiElement sibling = comment.getPrevSibling();
      final String commentText;
      if (sibling instanceof PsiWhiteSpace) {
        final String whiteSpaceText = sibling.getText();
        if (whiteSpaceText.startsWith("\n")) {
          commentText = whiteSpaceText.substring(1) + comment.getText();
        }
        else {
          commentText = comment.getText();
        }
      }
      else {
        commentText = comment.getText();
      }
      out.addComment(commentText);
      comment = getPrevSiblingOfType(comment, PsiComment.class, PsiStatement.class);
    }
  }

  private static void extractStatementComments(PsiElement element, IfStatementBranch out) {
    PsiComment comment = getPrevSiblingOfType(element, PsiComment.class, PsiStatement.class, PsiKeyword.class);
    while (comment != null) {
      final PsiElement sibling = comment.getPrevSibling();
      final String commentText;
      if (sibling instanceof PsiWhiteSpace) {
        final String whiteSpaceText = sibling.getText();
        if (whiteSpaceText.startsWith("\n")) {
          commentText = whiteSpaceText.substring(1) + comment.getText();
        }
        else {
          commentText = comment.getText();
        }
      }
      else {
        commentText = comment.getText();
      }
      out.addStatementComment(commentText);
      comment = getPrevSiblingOfType(comment, PsiComment.class, PsiStatement.class, PsiKeyword.class);
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

  private static void extractCaseExpressions(PsiExpression expression, PsiExpression switchExpression, IfStatementBranch values) {
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression argument = arguments[0];
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (EquivalenceChecker.expressionsAreEquivalent(switchExpression, argument)) {
        values.addCaseExpression(qualifierExpression);
      }
      else {
        values.addCaseExpression(argument);
      }
    }
    else if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.OROR.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          extractCaseExpressions(operand, switchExpression, values);
        }
      }
      else if (JavaTokenType.EQEQ.equals(tokenType) && operands.length == 2) {
        final PsiExpression lhs = operands[0];
        final PsiExpression rhs = operands[1];
        if (EquivalenceChecker.expressionsAreEquivalent(switchExpression, rhs)) {
          values.addCaseExpression(lhs);
        }
        else if (EquivalenceChecker.expressionsAreEquivalent(switchExpression, lhs)){
          values.addCaseExpression(rhs);
        }
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression contents = parenthesizedExpression.getExpression();
      extractCaseExpressions(contents, switchExpression, values);
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
        appendElement(child, renameBreakElements, breakLabelString, switchStatementText);
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
}