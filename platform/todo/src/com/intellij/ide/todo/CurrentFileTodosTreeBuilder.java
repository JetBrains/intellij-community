// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.todo.rpc.TodoRemoteClient;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

import static com.intellij.ide.todo.TodoImplementationChooserKt.shouldUseSplitTodo;

@ApiStatus.Internal
public final class CurrentFileTodosTreeBuilder extends TodoTreeBuilder {

  public CurrentFileTodosTreeBuilder(@NotNull JTree tree,
                                     @NotNull Project project) {
    super(tree, project);
  }

  @Override
  protected @NotNull TodoTreeStructure createTreeStructure() {
    return new CurrentFileTodosTreeStructure(myProject);
  }

  @Override
  protected void collectFiles(@NotNull Consumer<? super @NotNull PsiFile> consumer) {
    CurrentFileTodosTreeStructure treeStructure = (CurrentFileTodosTreeStructure)getTodoTreeStructure();
    PsiFile psiFile = treeStructure.getFile();

    if (psiFile != null) {
      if (shouldUseSplitTodo()) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
          TodoFilter filter = treeStructure.getTodoFilter();
          if (TodoRemoteClient.fileMatchesFilter(getProject(), virtualFile, filter)) {
            consumer.accept(psiFile);
          }
        }
      } else {
        if (treeStructure.accept(psiFile)) {
          consumer.accept(psiFile);
        }
      }
    }
  }

  /**
   * @see CurrentFileTodosTreeStructure#setFile
   */
  public void setFile(PsiFile file) {
    CurrentFileTodosTreeStructure treeStructure = (CurrentFileTodosTreeStructure)getTodoTreeStructure();
    treeStructure.setFile(file);
    rebuildCache();
  }
}
