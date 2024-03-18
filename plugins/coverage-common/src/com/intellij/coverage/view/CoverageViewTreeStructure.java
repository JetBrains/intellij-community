// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.BaseCoverageAnnotator;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class CoverageViewTreeStructure extends AbstractTreeStructure {
  private final Project myProject;
  private final AtomicReference<Object> myRootNode = new AtomicReference<>(null);
  final CoverageSuitesBundle myData;
  final CoverageViewManager.StateBean myStateBean;

  public CoverageViewTreeStructure(Project project, CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean) {
    myProject = project;
    myData = bundle;
    myStateBean = stateBean;
  }


  @NotNull
  @Override
  public Object getRootElement() {
    while (true) {
      Object root = myRootNode.get();
      if (root != null) return root;
      Object newRoot = myData.getCoverageEngine().createCoverageViewExtension(myProject, myData, myStateBean).createRootNode();
      if (myRootNode.compareAndSet(null, newRoot)) {
        if (myData.getCoverageEngine().getCoverageAnnotator(myProject) instanceof BaseCoverageAnnotator annotator) {
          annotator.setVcsFilteredChildren(false);
        }
        return newRoot;
      }
    }
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull final Object element) {
    if (element instanceof AbstractTreeNode<?> node) {
      return ArrayUtil.toObjectArray(node.getChildren());
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }


  @Override
  public Object getParentElement(@NotNull final Object element) {
    if (element instanceof AbstractTreeNode<?> node) {
      return node.getParent();
    }
    return null;
  }

  @Override
  @NotNull
  public NodeDescriptor createDescriptor(@NotNull final Object element, final NodeDescriptor parentDescriptor) {
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

