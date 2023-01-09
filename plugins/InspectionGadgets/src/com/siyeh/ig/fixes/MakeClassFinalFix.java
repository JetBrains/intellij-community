/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.MultiMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiModifier.ABSTRACT;
import static com.intellij.psi.PsiModifier.FINAL;

/**
 * @author Bas Leijdekkers
 */
public class MakeClassFinalFix extends InspectionGadgetsFix {

  private final String className;

  public MakeClassFinalFix(PsiClass aClass) {
    className = aClass.getName();
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message(
      "make.class.final.fix.name", className);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("make.class.final.fix.family.name");
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiClass containingClass = findClassToFix(element);
    if (containingClass == null) {
      return;
    }
    final PsiModifierList modifierList = containingClass.getModifierList();
    assert modifierList != null;
    if (!isOnTheFly()) {
      if (ClassInheritorsSearch.search(containingClass).findFirst() != null) {
        return;
      }
      WriteAction.run(() -> doMakeFinal(modifierList));
      return;
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final Query<PsiClass> search = ClassInheritorsSearch.search(containingClass);
    search.forEach(aClass -> {
      conflicts.putValue(containingClass, InspectionGadgetsBundle
        .message("0.will.no.longer.be.overridable.by.1", RefactoringUIUtil.getDescription(containingClass, false),
                 RefactoringUIUtil.getDescription(aClass, false)));
      return true;
    });
    final boolean conflictsDialogOK;
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog =
        new ConflictsDialog(element.getProject(), conflicts, () -> WriteAction.run(() -> doMakeFinal(modifierList)));
      conflictsDialogOK = conflictsDialog.showAndGet();
    }
    else {
      conflictsDialogOK = true;
    }
    if (conflictsDialogOK) {
      WriteAction.run(() -> doMakeFinal(modifierList));
    }
  }

  private static void doMakeFinal(PsiModifierList modifierList) {
    modifierList.setModifierProperty(FINAL, true);
    modifierList.setModifierProperty(ABSTRACT, false);
  }

  private static @Nullable PsiClass findClassToFix(PsiElement element) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (containingClass == null) {
      return null;
    }
    return containingClass.getModifierList() == null ? null : containingClass;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    final PsiClass aClass = findClassToFix(previewDescriptor.getPsiElement());
    if (aClass == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    final PsiModifierList modifierList = aClass.getModifierList();
    assert modifierList != null;
    doMakeFinal(modifierList);
    return IntentionPreviewInfo.DIFF;
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
