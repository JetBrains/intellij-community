// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.find.FindManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameRefactoringDialog;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class RefactoringUiService {

  public RenameRefactoringDialog createRenameRefactoringDialog(Project project,
                                                               PsiElement element,
                                                               PsiElement context,
                                                               Editor editor) {
    return null;
  }

  public int showReplacePromptDialog(boolean isMultipleFiles, @NlsContexts.DialogTitle String title, Project project) {
    return FindManager.PromptResult.SKIP;
  }

  public static RefactoringUiService getInstance() {
    return ApplicationManager.getApplication().getService(RefactoringUiService.class);
  }
}
