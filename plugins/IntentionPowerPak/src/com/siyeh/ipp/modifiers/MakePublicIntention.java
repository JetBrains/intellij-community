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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class MakePublicIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MakePublicPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiModifierListOwner owner = (PsiModifierListOwner)element.getParent();
    final PsiModifierList modifierList = owner.getModifierList();
    if (modifierList == null) {
      return;
    }
    if (!checkForConflicts(owner)) return;
    modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
  }

  private static boolean checkForConflicts(PsiModifierListOwner owner) {
    if (!(owner instanceof PsiClass)) {
      return true;
    }
    final PsiClass aClass = (PsiClass)owner;
    final PsiElement parent = aClass.getParent();
    if (!(parent instanceof PsiJavaFile)) {
      return true;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)parent;
    final String name = FileUtil.getNameWithoutExtension(javaFile.getName());
    final String className = aClass.getName();
    if (name.equals(className)) {
      return true;
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap();
    conflicts.putValue(aClass, "The " + RefactoringUIUtil.getDescription(aClass, false) + " is declared in " +
                               RefactoringUIUtil.getDescription(javaFile, false) +
                               " but when public should be declared in a file named '" + className + '\'');
    final ConflictsDialog conflictsDialog = new ConflictsDialog(owner.getProject(), conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }
}
