// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public final class CreateMissingDeconstructionRecordClassBranchesFix extends CreateMissingSwitchBranchesFix {

  private final List<String> allNames;

  private CreateMissingDeconstructionRecordClassBranchesFix(@NotNull PsiSwitchBlock block,
                                                            @NotNull Set<String> missedNames,
                                                            @NotNull List<String> allNames) {
    super(block, missedNames);
    this.allNames = allNames;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("create.missing.record.deconstructions.switch.branches.fix.family.name");
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiSwitchBlock block = myBlock.getElement();
    return block == null
           ? null
           : new CreateMissingDeconstructionRecordClassBranchesFix(PsiTreeUtil.findSameElementInCopy(block, target), this.myNames,
                                                                   this.allNames);
  }

  @Override
  protected @NotNull List<String> getAllNames(@NotNull PsiClass aClass) {
    return allNames;
  }

  @Override
  protected @NotNull Function<PsiSwitchLabelStatementBase, List<String>> getCaseExtractor() {
    return label -> {
      PsiCaseLabelElementList list = label.getCaseLabelElementList();
      if (list == null) return Collections.emptyList();
      return ContainerUtil.map(list.getElements(), PsiCaseLabelElement::getText);
    };
  }

  @Nullable
  public static CreateMissingDeconstructionRecordClassBranchesFix create(@NotNull PsiSwitchBlock switchBlock,
                                                                         @NotNull PsiClass selectorType,
                                                                         @NotNull Map<PsiType, Set<List<PsiType>>> missedBranches,
                                                                         @NotNull List<? extends PsiCaseLabelElement> elements) {
    if (missedBranches.isEmpty()) {
      return null;
    }
    if (!selectorType.isRecord()) {
      return null;
    }
    if (missedBranches.values().stream().flatMap(t -> t.stream()).flatMap(t -> t.stream())
      .anyMatch(type -> {
        if (type == null) {
          return true;
        }
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
        return (psiClass == null && !TypeConversionUtil.isPrimitiveAndNotNull(type)) ||
               (psiClass != null && psiClass.hasTypeParameters());
      })) {
      return null;
    }
    if (!SwitchUtils.isRuleFormatSwitch(switchBlock)) {
      return null;
    }
    List<String> allLabels = new ArrayList<>();
    int lastDeconstructionPatternIndex = -1;
    int i = -1;
    for (PsiCaseLabelElement element : elements) {
      i++;
      //put after last pattern, because missed cases cannot be dominated
      if (element instanceof PsiPattern) {
        lastDeconstructionPatternIndex = i;
      }
      if (element == null) return null;
      allLabels.add(element.getText());
    }
    List<String> missedLabels = getMissedLabels(switchBlock, missedBranches);
    if (missedLabels == null || missedLabels.isEmpty()) {
      return null;
    }
    allLabels.addAll(lastDeconstructionPatternIndex + 1, missedLabels);
    allLabels = allLabels.stream().distinct().toList();
    return new CreateMissingDeconstructionRecordClassBranchesFix(switchBlock, new HashSet<>(missedLabels), allLabels);
  }

  @Nullable
  private static List<String> getMissedLabels(@NotNull PsiSwitchBlock block,
                                              @NotNull Map<PsiType, Set<List<PsiType>>> branchesByType) {
    List<String> result = new ArrayList<>();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(block.getProject());
    for (Map.Entry<PsiType, Set<List<PsiType>>> branches : branchesByType.entrySet()) {
      PsiType recordType = branches.getKey();
      if (recordType == null) {
        return null;
      }
      String recordTypeString = recordType.getCanonicalText();
      List<String> variableNames = new ArrayList<>();
      PsiClass recordClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(recordType));
      if (recordClass == null || !recordClass.isRecord()) return null;
      for (PsiRecordComponent recordComponent : recordClass.getRecordComponents()) {
        String nextName = codeStyleManager.suggestUniqueVariableName(recordComponent.getName(), block, false);
        variableNames.add(nextName);
      }
      for (List<PsiType> branch : branches.getValue()) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        if (branch.size() != variableNames.size()) return null;
        for (int i = 0; i < branch.size(); i++) {
          PsiType psiType = branch.get(i);
          joiner.add(psiType.getCanonicalText() + " " + variableNames.get(i));
        }
        result.add(recordTypeString + joiner);
      }
    }
    //every item is equal, but it is needed to produce a stable result
    Collections.sort(result);
    return result;
  }
}
