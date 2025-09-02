// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.todo.nodes.SingleFileToDoNode;
import com.intellij.ide.todo.nodes.ToDoRootNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CurrentFileTodosTreeStructure extends TodoTreeStructure {
  /**
   * Current psi file for which the structure is built. If it is {@code null}
   * then the structure is empty (contains only root node).
   */
  private PsiFile myPsiFile;

  public CurrentFileTodosTreeStructure(Project project) {
    super(project);
  }

  @Override
  protected void validateCache() {
    super.validateCache();
    if (myPsiFile != null && !myPsiFile.isValid()) {
      VirtualFile vFile = myPsiFile.getVirtualFile();
      if (vFile.isValid()) {
        myPsiFile = PsiManager.getInstance(myProject).findFile(vFile);
      }
      else {
        myPsiFile = null;
      }
    }
  }

  @Nullable
  PsiFile getFile() {
    return myPsiFile;
  }

  /**
   * Sets {@code file} for which the structure is built. Alter this method is invoked caches should
   * be validated.
   */
  public void setFile(@Nullable PsiFile psiFile) {
    myPsiFile = psiFile;
    myRootElement = createRootElement();
  }

  @Override
  public boolean accept(@NotNull PsiFile psiFile) {
    if (myPsiFile == null || !myPsiFile.equals(psiFile) || !myPsiFile.isValid()) {
      return false;
    }
    return acceptTodoFilter(psiFile);
  }

  @Override
  boolean isAutoExpandNode(NodeDescriptor descriptor) {
    Object element = descriptor.getElement();
    if (element instanceof AbstractTreeNode) {
      element = ((AbstractTreeNode<?>)element).getValue();
    }
    if (element == myPsiFile) {
      return true;
    }
    else {
      return element == getRootElement() || element == mySummaryElement;
    }
  }

  @Override
  Object getFirstSelectableElement() {
    if (myRootElement instanceof SingleFileToDoNode) {
      return ((SingleFileToDoNode)myRootElement).getFileNode();
    }
    else {
      return null;
    }
  }

  @Override
  protected AbstractTreeNode createRootElement() {
    if (myPsiFile == null) {
      return new ToDoRootNode(myProject, new Object(), myBuilder, mySummaryElement);
    }
    else {
      return new SingleFileToDoNode(myProject, myPsiFile, myBuilder);
    }
  }
}