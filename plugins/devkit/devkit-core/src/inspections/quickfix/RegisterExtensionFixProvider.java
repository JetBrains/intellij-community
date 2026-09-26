// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.reference.UnusedDeclarationFixProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Set;

final class RegisterExtensionFixProvider implements UnusedDeclarationFixProvider {

  @Override
  public IntentionAction @NotNull [] getQuickFixes(@NotNull PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return IntentionAction.EMPTY_ARRAY;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass psiClass)) return IntentionAction.EMPTY_ARRAY;

    if (!PsiUtil.isPluginProject(element.getProject())) return IntentionAction.EMPTY_ARRAY;

    if (InheritanceUtil.isInheritor(psiClass, LocalInspectionTool.class.getName())) {
      return new IntentionAction[]{new RegisterInspectionFix(psiClass, LocalInspectionEP.LOCAL_INSPECTION)};
    }
    if (InheritanceUtil.isInheritor(psiClass, GlobalInspectionTool.class.getName())) {
      return new IntentionAction[]{new RegisterInspectionFix(psiClass, InspectionEP.GLOBAL_INSPECTION)};
    }

    ExtensionPointLocator extensionPointLocator = new ExtensionPointLocator(psiClass);
    Set<ExtensionPointCandidate> candidateList = extensionPointLocator.findSuperCandidates();
    if (!candidateList.isEmpty()) {
      return new IntentionAction[]{new RegisterExtensionFix(psiClass, candidateList)};
    }
    return IntentionAction.EMPTY_ARRAY;
  }
}
