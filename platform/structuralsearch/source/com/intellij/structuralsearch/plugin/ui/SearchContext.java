// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context of the search to be done
 */
public final class SearchContext {

  private final Project myProject;
  private final PsiFile myFile;
  private final Editor myEditor;

  public SearchContext(@NotNull Project project) {
    this(project, null, null);
  }

  public SearchContext(@NotNull DataContext context) {
    myProject = ObjectUtils.coalesce(CommonDataKeys.PROJECT.getData(context), ProjectManager.getInstance().getDefaultProject());
    PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    final VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (vFile != null && (file == null || !vFile.equals(file.getContainingFile().getVirtualFile()))) {
      file = PsiManager.getInstance(myProject).findFile(vFile);
    }
    myFile = file;
    myEditor = CommonDataKeys.EDITOR.getData(context);
  }

  SearchContext(@NotNull Project project, PsiFile file, Editor editor) {
    myProject = project;
    myFile = file;
    myEditor = editor;
  }

  @Nullable
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public Editor getEditor() {
    return myEditor;
  }
}
