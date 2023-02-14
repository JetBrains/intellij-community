// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class CreateEnumMissingSwitchBranchesFix extends CreateMissingSwitchBranchesFix {
  public CreateEnumMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, @NotNull Set<String> names) {
    super(block, names);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.missing.enum.switch.branches.fix.family.name");
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiSwitchBlock block = myBlock.getElement();
    return block == null ? null : new CreateEnumMissingSwitchBranchesFix(PsiTreeUtil.findSameElementInCopy(block, target), myNames);
  }

  @Override
  protected @NotNull List<String> getAllNames(@NotNull PsiClass aClass) {
    return StreamEx.of(aClass.getAllFields()).select(PsiEnumConstant.class).map(PsiField::getName).toList();
  }

  @Override
  protected @NotNull Function<PsiSwitchLabelStatementBase, List<String>> getCaseExtractor() {
    return label -> ContainerUtil.map(SwitchUtils.findEnumConstants(label), PsiEnumConstant::getName);
  }
}
