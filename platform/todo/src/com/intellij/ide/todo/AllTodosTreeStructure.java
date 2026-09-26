// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class AllTodosTreeStructure extends TodoTreeStructure {
  public AllTodosTreeStructure(final Project project) {
    super(project);
  }

  @Override
  public boolean accept(final @NotNull PsiFile psiFile) {
    return psiFile.isValid() && acceptTodoFilter(psiFile);
  }
}