// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public abstract class CreateMissingSwitchBranchesFix extends BaseSwitchFix {
  @NotNull
  protected final Set<String> myNames;

  public CreateMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, @NotNull Set<String> names) {
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
  protected void invoke() {
    PsiSwitchBlock switchBlock = myBlock.getElement();
    if (switchBlock == null) return;
    final PsiExpression switchExpression = switchBlock.getExpression();
    if (switchExpression == null) return;
    final PsiClassType switchType = (PsiClassType)switchExpression.getType();
    if (switchType == null) return;
    final PsiClass psiClass = switchType.resolve();
    if (psiClass == null) return;
    List<PsiSwitchLabelStatementBase> addedLabels = CreateSwitchBranchesUtil
      .createMissingBranches(switchBlock, getAllNames(psiClass), myNames, getCaseExtractor());
    CreateSwitchBranchesUtil.createTemplate(switchBlock, addedLabels);
  }

  abstract protected @NotNull List<String> getAllNames(@NotNull PsiClass aClass);
  abstract protected @NotNull Function<PsiSwitchLabelStatementBase, List<String>> getCaseExtractor();
}
