// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class CoverageViewTreeStructure extends AbstractTreeStructure {
  private final Project myProject;
  final CoverageSuitesBundle myData;
  final CoverageViewManager.StateBean myStateBean;
  private final CoverageListRootNode myRootNode;

  public CoverageViewTreeStructure(Project project, CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean) {
    myProject = project;
    myData = bundle;
    myStateBean = stateBean;
    myRootNode = (CoverageListRootNode)bundle.getCoverageEngine().createCoverageViewExtension(project, bundle, stateBean).createRootNode();
  }


  @NotNull
  @Override
  public Object getRootElement() {
    return myRootNode;
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull final Object element) {
    if (element instanceof CoverageListNode) {
      return ArrayUtil.toObjectArray(((CoverageListNode)element).getChildren());
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }


  @Override
  public Object getParentElement(@NotNull final Object element) {
    if (element instanceof CoverageListNode) {
      return ((CoverageListNode)element).getParent();
    }
    return null;
  }

  @Override
  @NotNull
  public NodeDescriptor createDescriptor(@NotNull final Object element, final NodeDescriptor parentDescriptor) {
    if (element instanceof CoverageListNode) {
      return (CoverageListNode)element;
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
}

