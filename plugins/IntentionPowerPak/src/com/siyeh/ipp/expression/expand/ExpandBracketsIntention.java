// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression.expand;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ExpandBracketsIntention extends BaseElementAtCaretIntentionAction {

  private boolean myStartInWriteAction = false;

  private static final Pass<PsiParenthesizedExpression> EXPAND_CALLBACK = new Pass<PsiParenthesizedExpression>() {
    @Override
    public void pass(@NotNull PsiParenthesizedExpression expression) {
      WriteCommandAction.writeCommandAction(expression.getProject(), expression.getContainingFile())
        .withName(getName())
        .run(() -> replaceExpression(expression));
    }
  };

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @NotNull
  @Override
  public String getText() {
    return getName();
  }

  private static String getName() {
    return IntentionPowerPackBundle.defaultableMessage("expand.brackets.intention.name");
  }

  @Override
  public boolean startInWriteAction() {
    return myStartInWriteAction;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    List<PsiParenthesizedExpression> possibleInnerExpressions = getPossibleInnerExpressions(element);
    if (possibleInnerExpressions == null || possibleInnerExpressions.isEmpty()) return false;
    myStartInWriteAction = possibleInnerExpressions.size() == 1;
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    List<PsiParenthesizedExpression> possibleInnerExpressions = getPossibleInnerExpressions(element);
    if (possibleInnerExpressions == null) return;
    processInnerExpression(editor, possibleInnerExpressions);
  }

  private static void processInnerExpression(@Nullable Editor editor, @NotNull List<PsiParenthesizedExpression> expressions) {
    if (expressions.size() == 1) {
      replaceExpression(expressions.get(0));
      return;
    }
    if (expressions.isEmpty() || editor == null) return;
    IntroduceTargetChooser.showChooser(editor, expressions, EXPAND_CALLBACK, new PsiExpressionTrimRenderer.RenderFunction());
  }

  private static void replaceExpression(@NotNull PsiParenthesizedExpression parenthesized) {
    ExpandableExpression expression = createExpandableExpression(parenthesized);
    if (expression == null) return;
    StringBuilder sb = new StringBuilder();
    CommentTracker commentTracker = new CommentTracker();
    PsiExpression toReplace = expression.getExpressionToReplace();
    PsiPolyadicExpression outerExpression = ObjectUtils.tryCast(toReplace.getParent(), PsiPolyadicExpression.class);
    if (outerExpression == null || ExpandUtils.getOperator(outerExpression.getOperationTokenType()) == null) {
      if (!expression.expand(null, sb)) return;
      PsiReplacementUtil.replaceExpression(toReplace, sb.toString(), commentTracker);
      return;
    }
    for (PsiExpression operand : outerExpression.getOperands()) {
      PsiJavaToken tokenBefore = outerExpression.getTokenBeforeOperand(operand);
      if (operand == toReplace) {
        if (!expression.expand(tokenBefore, sb)) return;
        continue;
      }
      if (tokenBefore != null) sb.append(tokenBefore.getText());
      sb.append(operand.getText());
    }
    PsiReplacementUtil.replaceExpression(outerExpression, sb.toString(), commentTracker);
  }

  @Nullable
  private static ExpandableExpression createExpandableExpression(@NotNull PsiParenthesizedExpression parenthesized) {
    DistributiveExpression distributive = DistributiveExpression.create(parenthesized);
    AssociativeExpression additive = AssociativeExpression.create(parenthesized);
    if (distributive == null) return additive;
    if (additive == null) return distributive;
    if (distributive.getExpression() == null) return additive;
    return distributive;
  }

  @Nullable
  private static List<PsiParenthesizedExpression> getPossibleInnerExpressions(@NotNull PsiElement element) {
    if (!(element instanceof PsiJavaToken)) return null;
    List<PsiParenthesizedExpression> possibleExpressions = new ArrayList<>();
    while ((element = PsiTreeUtil.getParentOfType(element, PsiParenthesizedExpression.class)) != null) {
      PsiParenthesizedExpression parenthesized = (PsiParenthesizedExpression)element;
      if (DistributiveExpression.create(parenthesized) != null || AssociativeExpression.create(parenthesized) != null) {
        possibleExpressions.add(parenthesized);
      }
    }
    return possibleExpressions;
  }
}
