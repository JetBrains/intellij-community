// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.todo.model.TodoScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;
import java.util.function.Consumer;

import static com.intellij.ide.vfs.VirtualFileIdKt.rpcId;

@ApiStatus.Internal
public final class CurrentFileTodosTreeBuilder extends TodoTreeBuilder {

  public CurrentFileTodosTreeBuilder(@NotNull JTree tree,
                                     @NotNull Project project) {
    super(tree, project);
  }

  @Override
  public @Nullable TodoScope getScope() {
    VirtualFile file = getCurrentFile();
    if (file == null) return null;
    return new TodoScope.CurrentFile(rpcId(file));
  }

  @Override
  protected @NotNull TodoTreeStructure createTreeStructure() {
    return new CurrentFileTodosTreeStructure(myProject);
  }

  @ApiStatus.Internal
  private @Nullable VirtualFile getCurrentFile() {
    CurrentFileTodosTreeStructure treeStructure = (CurrentFileTodosTreeStructure)getTodoTreeStructure();
    PsiFile psiFile = treeStructure.getFile();
    return psiFile == null ? null : psiFile.getVirtualFile();
  }

  @Override
  protected void collectFiles(@NotNull Consumer<? super @NotNull PsiFile> consumer) {
    CurrentFileTodosTreeStructure treeStructure = (CurrentFileTodosTreeStructure)getTodoTreeStructure();
    PsiFile psiFile = treeStructure.getFile();

    if (psiFile != null) {
      if (treeStructure.accept(psiFile)) {
        consumer.accept(psiFile);
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
