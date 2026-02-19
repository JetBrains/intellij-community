// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class ChangeListTodosTreeStructure extends TodoTreeStructure {
  public ChangeListTodosTreeStructure(Project project) {
    super(project);
  }

  @Override
  public boolean accept(final @NotNull PsiFile psiFile) {
    if (!psiFile.isValid()) return false;

    VirtualFile file = psiFile.getVirtualFile();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    List<LocalChangeList> changeLists = changeListManager.getChangeLists(file);
    return ContainerUtil.exists(changeLists, list -> list.isDefault()) &&
           acceptTodoFilter(psiFile);
  }
}
