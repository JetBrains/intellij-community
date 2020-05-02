// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class CreateMissingSwitchBranchesFix extends BaseSwitchFix {
  private final Set<String> myNames;

  public CreateMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, Set<String> names) {
    super(block);
    myNames = names;
  }

  @Override
  public @NotNull String getText() {
    return getName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
    return CreateSwitchBranchesUtil.getActionName(myNames);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.missing.switch.branches.fix.family.name");
  }

  @Override
  protected void invoke() {
    PsiSwitchBlock switchBlock = myBlock.getElement();
    if (switchBlock == null) return;
    final PsiExpression switchExpression = switchBlock.getExpression();
    if (switchExpression == null) return;
    final PsiClassType switchType = (PsiClassType)switchExpression.getType();
    if (switchType == null) return;
    final PsiClass enumClass = switchType.resolve();
    if (enumClass == null) return;
    List<String> allEnumConstants = StreamEx.of(enumClass.getAllFields()).select(PsiEnumConstant.class).map(PsiField::getName).toList();
    Function<PsiSwitchLabelStatementBase, List<String>> caseExtractor =
      label -> ContainerUtil.map(SwitchUtils.findEnumConstants(label), PsiEnumConstant::getName);
    List<PsiSwitchLabelStatementBase> addedLabels = CreateSwitchBranchesUtil
      .createMissingBranches(switchBlock, allEnumConstants, myNames, caseExtractor);
    CreateSwitchBranchesUtil.createTemplate(switchBlock, addedLabels);
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiSwitchBlock block = myBlock.getElement();
    return block == null ? null : new CreateMissingSwitchBranchesFix(PsiTreeUtil.findSameElementInCopy(block, target), myNames);
  }
}
