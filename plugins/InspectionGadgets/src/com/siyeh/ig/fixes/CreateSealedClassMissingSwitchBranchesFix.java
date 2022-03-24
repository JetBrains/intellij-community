// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class CreateSealedClassMissingSwitchBranchesFix extends CreateMissingSwitchBranchesFix {
  @NotNull
  private final List<String> myAllNames;

  public CreateSealedClassMissingSwitchBranchesFix(@NotNull PsiSwitchBlock block, Set<String> names, @NotNull List<String> allNames) {
    super(block, names);
    myAllNames = allNames;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.missing.sealed.class.switch.branches.fix.family.name");
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiSwitchBlock block = myBlock.getElement();
    return block == null
           ? null
           : new CreateSealedClassMissingSwitchBranchesFix(PsiTreeUtil.findSameElementInCopy(block, target), myNames, myAllNames);
  }

  @Override
  protected @NotNull List<String> getAllNames(@NotNull PsiClass ignored) {
    return myAllNames;
  }

  @Override
  protected @NotNull Function<PsiSwitchLabelStatementBase, List<String>> getCaseExtractor() {
    return label -> {
      PsiCaseLabelElementList list = label.getCaseLabelElementList();
      if (list == null) return Collections.emptyList();
      return ContainerUtil.map(list.getElements(), PsiCaseLabelElement::getText);
    };
  }
}
