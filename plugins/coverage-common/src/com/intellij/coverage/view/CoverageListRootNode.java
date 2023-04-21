// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.observable.properties.AbstractObservableProperty;
import com.intellij.openapi.observable.properties.ObservableProperty;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CoverageListRootNode extends CoverageListNode {
  private volatile List<AbstractTreeNode<?>> myTopLevelPackages;
  private final State myState = new State();
  private final ObservableState myObservable = new ObservableState();

  public CoverageListRootNode(Project project, @NotNull PsiNamedElement classOrPackage,
                              CoverageSuitesBundle bundle,
                              CoverageViewManager.StateBean stateBean) {
    super(project, classOrPackage, bundle, stateBean, false);
  }

  void setHasVCSFilteredChildren(boolean newValue) {
    myState.myHasVCSFilteredChildren = newValue;
  }

  void setHasFullyCoveredChildren(boolean newValue) {
    myState.myHasFullyCoveredChildren = newValue;
  }

  public ObservableProperty<State> getState() {
    return myObservable;
  }

  @Override
  public synchronized void reset() {
    super.reset();
    myTopLevelPackages = null;
  }

  private synchronized List<AbstractTreeNode<?>> getTopLevelPackages(CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean, Project project) {
    if (myTopLevelPackages == null) {
      setHasVCSFilteredChildren(false);
      setHasFullyCoveredChildren(false);
      final var nodes = bundle.getCoverageEngine().createCoverageViewExtension(project, bundle, stateBean).createTopLevelNodes();
      for (AbstractTreeNode<?> abstractTreeNode : nodes) {
        abstractTreeNode.setParent(this);
      }
      myTopLevelPackages = filterChildren(nodes);
      myObservable.fire();
    }
    return myTopLevelPackages;
  }

  @NotNull
  @Override
  public List<? extends AbstractTreeNode<?>> getChildren() {
    if (myStateBean.myFlattenPackages) {
      return getTopLevelPackages(myBundle, myStateBean, myProject);
    }
    setHasVCSFilteredChildren(false);
    setHasFullyCoveredChildren(false);
    final var children = super.getChildren();
    myObservable.fire();
    return children;
  }

  private class ObservableState extends AbstractObservableProperty<State> {

    @Override
    public State get() {
      return myState;
    }

    public void fire() {
      fireChangeEvent(get());
    }
  }

  public static class State {
    public boolean myHasVCSFilteredChildren = false;
    public boolean myHasFullyCoveredChildren = false;
  }
}
