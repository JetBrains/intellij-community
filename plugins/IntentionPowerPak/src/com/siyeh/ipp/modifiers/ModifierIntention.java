/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.MultiMap;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
abstract class ModifierIntention extends Intention implements LowPriorityAction {

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @NotNull
  @Override
  protected final PsiElementPredicate getElementPredicate() {
    return new ModifierPredicate(getModifier());
  }

  @Override
  protected final void processIntention(@NotNull PsiElement element) {
    final PsiMember member = (PsiMember)element.getParent();
    final PsiModifierList modifierList = member.getModifierList();
    if (modifierList == null) {
      return;
    }
    final MultiMap<PsiElement, String> conflicts = checkForConflicts(member);
    if (conflicts == null) {
      //canceled by user
      return;
    }
    final Project project = member.getProject();
    final boolean conflictsDialogOK;
    if (conflicts.isEmpty()) {
      conflictsDialogOK = true;
    } else {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts, () -> changeModifier(modifierList));
      conflictsDialogOK = conflictsDialog.showAndGet();
    }
    if (conflictsDialogOK) {
      changeModifier(modifierList);
    }
  }

  private void changeModifier(PsiModifierList modifierList) {
    PsiElement parent = modifierList.getParent();
    @VisibilityConstant final String modifier = getModifier();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      //no myPrepareSuccessfulSwingThreadCallback means that the conflicts when any, won't be shown again
      new ChangeSignatureProcessor(parent.getProject(),
                                   method,
                                   false,
                                   modifier,
                                   method.getName(),
                                   method.getReturnType(),
                                   ParameterInfoImpl.fromMethod(method),
                                   JavaThrownExceptionInfo.extractExceptions(method))
        .run();
      return;
    }
    WriteAction.run(() -> {
      modifierList.setModifierProperty(modifier, true);
      if (!PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
        final Project project = modifierList.getProject();
        final PsiElement whitespace = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(" ");
        final PsiElement sibling = modifierList.getNextSibling();
        if (sibling instanceof PsiWhiteSpace) {
          sibling.replace(whitespace);
          CodeStyleManager.getInstance(project).reformatRange(parent, modifierList.getTextOffset(),
                                                              modifierList.getNextSibling().getTextOffset());
        }
      }
    });
  }

  private MultiMap<PsiElement, String> checkForConflicts(@NotNull final PsiMember member) {
    if (member instanceof PsiClass && getModifier().equals(PsiModifier.PUBLIC)) {
      final PsiClass aClass = (PsiClass)member;
      final PsiElement parent = aClass.getParent();
      if (!(parent instanceof PsiJavaFile)) {
        return MultiMap.emptyInstance();
      }
      final PsiJavaFile javaFile = (PsiJavaFile)parent;
      final String name = FileUtil.getNameWithoutExtension(javaFile.getName());
      final String className = aClass.getName();
      if (name.equals(className)) {
        return MultiMap.emptyInstance();
      }
      final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
      conflicts.putValue(aClass, IntentionPowerPackBundle.message(
        "0.is.declared.in.1.but.when.public.should.be.declared.in.a.file.named.2",
        RefactoringUIUtil.getDescription(aClass, false),
        RefactoringUIUtil.getDescription(javaFile, false),
        CommonRefactoringUtil.htmlEmphasize(className + ".java")));
      return conflicts;
    }
    final PsiModifierList modifierList = member.getModifierList();
    if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      return MultiMap.emptyInstance();
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      if (member instanceof PsiMethod) {
        JavaChangeSignatureUsageProcessor.ConflictSearcher.searchForHierarchyConflicts((PsiMethod)member, conflicts, getModifier());
      }
      final PsiModifierList modifierListCopy = ReadAction.compute(() -> {
        PsiModifierList copy = (PsiModifierList)modifierList.copy();
        copy.setModifierProperty(getModifier(), true);
        return copy;
      });
      
      final Query<PsiReference> search = ReferencesSearch.search(member, member.getUseScope());
      search.forEach(reference -> {
        final PsiElement element = reference.getElement();
        if (JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, element, null, null)) {
          return true;
        }
        final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiField.class, PsiClass.class, PsiFile.class);
        if (context == null) {
          return true;
        }
        conflicts.putValue(element, RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                                              RefactoringUIUtil.getDescription(member, false),
                                                              PsiBundle.visibilityPresentation(getModifier()),
                                                              RefactoringUIUtil.getDescription(context, true)));
        return true;
      });
    }, RefactoringBundle.message("detecting.possible.conflicts"), true, member.getProject())) {
      return null;
    }
    return conflicts;
  }

  @VisibilityConstant
  protected abstract String getModifier();

  @MagicConstant(stringValues = {PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL})
  @interface VisibilityConstant {}
}
