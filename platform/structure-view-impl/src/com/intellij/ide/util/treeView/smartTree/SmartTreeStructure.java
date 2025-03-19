// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

public class SmartTreeStructure extends AbstractTreeStructure {
  protected final @NotNull TreeModel myModel;
  protected final Project myProject;
  private TreeElementWrapper myRootElementWrapper;

  public SmartTreeStructure(@NotNull Project project, @NotNull TreeModel model) {
    myModel = model;
    myProject = project;
  }

  @Override
  public void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  @Override
  public @NotNull ActionCallback asyncCommit() {
    return asyncCommitDocuments(myProject);
  }

  @Override
  public boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  @Override
  public @NotNull NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    return (AbstractTreeNode)element;
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull Object element) {
    return ((AbstractTreeNode<?>)element).getChildren().toArray();
  }

  @Override
  public Object getParentElement(@NotNull Object element) {
    return ((AbstractTreeNode<?>)element).getParent();
  }

  @Override
  public @NotNull Object getRootElement() {
    if (myRootElementWrapper == null){
      myRootElementWrapper = createTree();
    }
    return myRootElementWrapper;
  }

  protected @NotNull TreeElementWrapper createTree() {
    return new TreeElementWrapper(myProject, myModel.getRoot(), myModel);
  }

  @Override
  public boolean isAlwaysLeaf(@NotNull Object element) {
    return ((AbstractTreeNode<?>)element).isAlwaysLeaf();
  }

  public void rebuildTree() {
    ((CachingChildrenTreeNode<?>)getRootElement()).rebuildChildren();
  }
}
