// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.rename.RenameRefactoringDialog;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.NlsContexts.*;

@ApiStatus.Experimental
public class RefactoringUiService {

  public RenameRefactoringDialog createRenameRefactoringDialog(Project project,
                                                               PsiElement element,
                                                               PsiElement context,
                                                               Editor editor) {
    return null;
  }

  public int showReplacePromptDialog(boolean isMultipleFiles, @DialogTitle String title, Project project) {
    return FindManager.PromptResult.SKIP;
  }

  public void setStatusBarInfo(@NotNull Project project, @NotNull @StatusBarText String message) {
  }

  public ConflictsDialogBase createConflictsDialog(@NotNull Project project,
                                                   @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                   @Nullable Runnable doRefactoringRunnable,
                                                   boolean alwaysShowOkButton,
                                                   boolean canShowConflictsInView) {
    return null;
  }

  public void startFindUsages(PsiElement element, FindUsagesOptions options) {

  }

  public void highlightUsageReferences(PsiElement file,
                                       PsiElement target,
                                       @NotNull Editor editor, boolean clearHighlights) {

  }

  public void findUsages(@NotNull Project project, @NotNull PsiElement psiElement, @Nullable PsiFile scopeFile, FileEditor editor, boolean showDialog, @Nullable("null means default (stored in options)") SearchScope searchScope) {
  }

  public boolean showRefactoringMessageDialog(@DialogTitle String title, @DialogMessage String message,
                                              @NonNls String helpTopic, @NonNls String iconId, boolean showCancelButton, Project project) {
    return false;
  }


  public static RefactoringUiService getInstance() {
    return ApplicationManager.getApplication().getService(RefactoringUiService.class);
  }
}