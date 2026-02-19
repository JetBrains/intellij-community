// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
public final class CoverageViewTreeStructure extends AbstractTreeStructure {
  private final Project myProject;
  private final AtomicReference<Object> myRootNode = new AtomicReference<>(null);
  final CoverageSuitesBundle myData;

  public CoverageViewTreeStructure(Project project, CoverageSuitesBundle bundle) {
    myProject = project;
    myData = bundle;
  }

  @Override
  public @NotNull Object getRootElement() {
    while (true) {
      Object root = myRootNode.get();
      if (root != null) return root;
      CoverageViewExtension viewExtension = myData.getCoverageEngine().createCoverageViewExtension(myProject, myData);
      Object newRoot = viewExtension.createRootNode();
      if (myRootNode.compareAndSet(null, newRoot)) {
        viewExtension.onRootReset();
        return newRoot;
      }
    }
  }

  @Override
  public Object @NotNull [] getChildElements(final @NotNull Object element) {
    if (element instanceof AbstractTreeNode<?> node) {
      return ArrayUtil.toObjectArray(node.getChildren());
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }


  @Override
  public Object getParentElement(final @NotNull Object element) {
    if (element instanceof AbstractTreeNode<?> node) {
      return node.getParent();
    }
    return null;
  }

  @Override
  public @NotNull NodeDescriptor<?> createDescriptor(final @NotNull Object element, final NodeDescriptor parentDescriptor) {
    if (element instanceof AbstractTreeNode<?> node) {
      return node;
    }
    return new CoverageViewDescriptor(myProject, parentDescriptor, element);
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  public synchronized void reset() {
    myRootNode.set(null);
  }
}

