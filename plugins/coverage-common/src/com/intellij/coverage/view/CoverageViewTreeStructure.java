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

public class CoverageViewTreeStructure extends AbstractTreeStructure {
  private final Project myProject;
  final CoverageSuitesBundle myData;
  final CoverageViewManager.StateBean myStateBean;
  private Object myRootNode;

  public CoverageViewTreeStructure(Project project, CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean) {
    myProject = project;
    myData = bundle;
    myStateBean = stateBean;
  }


  @NotNull
  @Override
  synchronized public Object getRootElement() {
    if (myRootNode == null) {
      if (myData.getCoverageEngine().getCoverageAnnotator(myProject) instanceof BaseCoverageAnnotator annotator) {
        annotator.setVcsFilteredChildren(false);
      }
      myRootNode = myData.getCoverageEngine().createCoverageViewExtension(myProject, myData, myStateBean).createRootNode();
    }
    return myRootNode;
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
    myRootNode = null;
  }
}

