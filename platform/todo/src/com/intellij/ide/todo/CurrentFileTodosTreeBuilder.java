// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;
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

  @ApiStatus.Internal
  public @Nullable VirtualFile getCurrentFile() {
    CurrentFileTodosTreeStructure treeStructure = (CurrentFileTodosTreeStructure)getTodoTreeStructure();
    PsiFile psiFile = treeStructure.getFile();
    return psiFile == null ? null : psiFile.getVirtualFile();
  }

  @Override
  protected void collectFiles(@NotNull Consumer<? super @NotNull PsiFile> consumer) {
    CurrentFileTodosTreeStructure treeStructure = (CurrentFileTodosTreeStructure)getTodoTreeStructure();
    PsiFile psiFile = treeStructure.getFile();

    if (psiFile != null) {
      if (shouldUseSplitTodo()) {
        getCoroutineHelper().collectCurrentFileWithCachedTodos(psiFile, treeStructure.getTodoFilter(), consumer);
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
