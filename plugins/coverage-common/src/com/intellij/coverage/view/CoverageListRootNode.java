// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CoverageListRootNode extends CoverageListNode {
  private volatile List<AbstractTreeNode<?>> myTopLevelPackages;

  public CoverageListRootNode(Project project, @NotNull PsiNamedElement classOrPackage,
                              CoverageSuitesBundle bundle,
                              CoverageViewManager.StateBean stateBean) {
    super(project, classOrPackage, bundle, stateBean);
  }

  public synchronized void reset() {
    myTopLevelPackages = null;
  }

  private synchronized List<AbstractTreeNode<?>> getTopLevelPackages(CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean, Project project) {
    if (myTopLevelPackages == null) {
      myTopLevelPackages = bundle.getCoverageEngine().createCoverageViewExtension(project, bundle, stateBean).createTopLevelNodes();
      for (AbstractTreeNode abstractTreeNode : myTopLevelPackages) {
        abstractTreeNode.setParent(this);
      }
    }
    return myTopLevelPackages;
  }

  @NotNull
  @Override
  public List<? extends AbstractTreeNode<?>> getChildren() {
    if (myStateBean.myFlattenPackages) {
      return getTopLevelPackages(myBundle, myStateBean, myProject);
    }
    return super.getChildren();
  }
}
