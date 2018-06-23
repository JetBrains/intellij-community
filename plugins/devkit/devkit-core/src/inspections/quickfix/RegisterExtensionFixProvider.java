/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.Set;

public class RegisterExtensionFixProvider implements UnusedDeclarationFixProvider {

  @NotNull
  @Override
  public IntentionAction[] getQuickFixes(@NotNull PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return IntentionAction.EMPTY_ARRAY;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass)) return IntentionAction.EMPTY_ARRAY;

    PsiClass psiClass = (PsiClass)parent;
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
