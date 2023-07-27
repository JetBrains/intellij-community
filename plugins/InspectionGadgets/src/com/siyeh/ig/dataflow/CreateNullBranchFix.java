// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.BaseSwitchFix;
import com.siyeh.ig.fixes.CreateDefaultBranchFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CreateNullBranchFix extends BaseSwitchFix {

  public CreateNullBranchFix(@NotNull PsiSwitchBlock block) {
    super(block);
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.null.branch.fix.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiSwitchBlock switchBlock, @NotNull ModPsiUpdater updater) {
    if (!HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(switchBlock)) return;
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return;
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return;
    List<PsiElement> branches = SwitchUtils.getSwitchBranches(switchBlock);
    for (PsiElement branch : branches) {
      // just for the case if we already contain null, there is no need to apply the fix
      if (branch instanceof PsiExpression expression && TypeConversionUtil.isNullType(expression.getType())) return;
    }
    PsiElement unconditionalLabel = findUnconditionalLabel(switchBlock);
    PsiElement bodyElement = body.getFirstBodyElement();
    if (bodyElement instanceof PsiWhiteSpace && bodyElement == body.getLastBodyElement()) {
      bodyElement.delete();
    }
    PsiElement anchor = unconditionalLabel != null ? unconditionalLabel : body.getRBrace();
    if (anchor == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
    generateStatements(switchBlock, SwitchUtils.isRuleFormatSwitch(switchBlock), unconditionalLabel)
      .stream()
      .map(text -> factory.createStatementFromText(text, body))
      .forEach(statement -> body.addBefore(statement, anchor));
    CreateDefaultBranchFix.startTemplateOnStatement(PsiTreeUtil.getPrevSiblingOfType(anchor, PsiStatement.class), updater);
  }

  @Nullable
  private static PsiElement findUnconditionalLabel(@NotNull PsiSwitchBlock switchBlock) {
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return null;
    return ContainerUtil.find(body.getStatements(),
                              stmt -> stmt instanceof PsiSwitchLabelStatementBase label && SwitchUtils.isUnconditionalLabel(label));
  }

  private static @NonNls List<String> generateStatements(@NotNull PsiSwitchBlock switchBlock, boolean isRuleBasedFormat,
                                                         @Nullable PsiElement defaultElement) {
    PsiStatement previousStatement;
    if (defaultElement == null) {
      previousStatement = ArrayUtil.getLastElement(Objects.requireNonNull(switchBlock.getBody()).getStatements());
    }
    else {
      previousStatement = PsiTreeUtil.getPrevSiblingOfType(defaultElement, PsiStatement.class);
    }
    List<String> result = new ArrayList<>();
    if (!isRuleBasedFormat && previousStatement != null && ControlFlowUtils.statementMayCompleteNormally(previousStatement)) {
      result.add("break;");
    }
    result.addAll(CreateSwitchBranchesUtil.generateStatements("null", switchBlock, isRuleBasedFormat, false));
    return result;
  }
}
