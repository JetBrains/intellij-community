// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProjectFileNode {
  @NotNull
  Module getModule();

  @NotNull
  VirtualFile getVirtualFile();

  default boolean isValid() {
    return !getModule().isDisposed() && getVirtualFile().isValid();
  }

  @Nullable
  default PsiElement getPsiElement() {
    VirtualFile file = getVirtualFile();
    if (!file.isValid()) return null;
    Module module = getModule();
    if (module.isDisposed()) return null;
    Project project = module.getProject();
    if (project.isDisposed()) return null;
    return file.isDirectory()
           ? PsiManager.getInstance(project).findDirectory(file)
           : PsiManager.getInstance(project).findFile(file);
  }
}
