/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.modifiers;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
abstract class ModifierIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ModifierPredicate(getModifier());
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiMember member = (PsiMember)element.getParent();
    final PsiModifierList modifierList = member.getModifierList();
    if (modifierList == null) {
      return;
    }
    if (!checkForConflicts(member)) return;
    modifierList.setModifierProperty(getModifier(), true);
  }

  private boolean checkForConflicts(final PsiMember member) {
    final PsiModifierList modifierList = member.getModifierList();
    if (modifierList == null) {
      return false;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      return true;
    }
    final PsiModifierList modifierListCopy = (PsiModifierList)modifierList.copy();
    modifierListCopy.setModifierProperty(getModifier(), true);
    final List<PsiElement> problemReferences = new ArrayList();
    final Query<PsiReference> search = ReferencesSearch.search(member, member.getResolveScope());
    search.forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        final PsiElement element = reference.getElement();
        if (!JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, element, null, null)) {
          problemReferences.add(element);
        }
        return true;
      }
    });
    if (problemReferences.isEmpty()) {
      return true;
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap();
    for (PsiElement reference : problemReferences) {
      final PsiElement context =
        PsiTreeUtil.getParentOfType(reference, PsiMethod.class, PsiField.class, PsiClass.class, PsiFile.class);
      conflicts.putValue(reference, RefactoringUIUtil.getDescription(member, false) +
                                    " with " + getModifier() + " visibility won't be accessible from " +
                                    RefactoringUIUtil.getDescription(context, true));
    }
    final ConflictsDialog conflictsDialog = new ConflictsDialog(member.getProject(), conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }

  @PsiModifier.ModifierConstant
  protected abstract String getModifier();
}
