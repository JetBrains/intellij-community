// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.BaseSwitchFix;
import com.siyeh.ig.fixes.CreateDefaultBranchFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CreateNullBranchFix extends BaseSwitchFix {

  public CreateNullBranchFix(@NotNull PsiSwitchBlock block) {
    super(block);
  }

  @Override
  public @NotNull String getText() {
    return getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.null.branch.fix.family.name");
  }

  @Override
  protected void invoke() {
    PsiSwitchBlock switchBlock = myBlock.getElement();
    if (switchBlock == null) return;
    if (!HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(switchBlock)) return;
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return;
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return;
    List<PsiElement> branches = SwitchUtils.getSwitchBranches(switchBlock);
    for (PsiElement branch : branches) {
      // jus for the case if we already contain null or total pattern, there is no need to apply the fix
      if (branch instanceof PsiExpression && TypeConversionUtil.isNullType(((PsiExpression)branch).getType())) return;
      if (branch instanceof PsiPattern && JavaPsiPatternUtil.isTotalForType(((PsiPattern)branch), selectorType)) return;
    }
    PsiElement defaultElement = SwitchUtils.findDefaultElement(switchBlock);
    PsiElement anchor;
    if (defaultElement instanceof PsiSwitchLabelStatementBase) {
      anchor = defaultElement;
    }
    else if (defaultElement instanceof PsiDefaultCaseLabelElement) {
      defaultElement = defaultElement.getParent().getParent();
      anchor = defaultElement;
    }
    else {
      anchor = body.getRBrace();
    }
    if (anchor == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    generateStatements(switchBlock, SwitchUtils.isRuleFormatSwitch(switchBlock), defaultElement)
      .stream()
      .map(text -> factory.createStatementFromText(text, body))
      .forEach(statement -> body.addBefore(statement, anchor));
    CreateDefaultBranchFix.adjustEditor(switchBlock, PsiTreeUtil.getPrevSiblingOfType(anchor, PsiStatement.class));
  }

  private static @NonNls List<String> generateStatements(@NotNull PsiSwitchBlock switchBlock, boolean isRuleBasedFormat,
                                                         @Nullable PsiElement defaultElement) {
    Project project = switchBlock.getProject();
    PsiStatement statement =
      JavaPsiFacade.getElementFactory(project).createStatementFromText("throw new java.lang.NullPointerException();", switchBlock);
    if (isRuleBasedFormat) {
      return Collections.singletonList("case null -> " + statement.getText());
    }
    PsiStatement previousStatement;
    if (defaultElement == null) {
      previousStatement = ArrayUtil.getLastElement(Objects.requireNonNull(switchBlock.getBody()).getStatements());
    }
    else {
      previousStatement = PsiTreeUtil.getPrevSiblingOfType(defaultElement, PsiStatement.class);
    }
    if (previousStatement != null && ControlFlowUtils.statementMayCompleteNormally(previousStatement)) {
      return Arrays.asList("break;", "case null:", statement.getText());
    }
    return Arrays.asList("case null:", statement.getText());
  }
}
