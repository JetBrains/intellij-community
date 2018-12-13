package com.intellij.coverage.view;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class CoverageViewTreeStructure extends AbstractTreeStructure {
  private final Project myProject;
  final CoverageSuitesBundle myData;
  final CoverageViewManager.StateBean myStateBean;
  private final CoverageListRootNode myRootNode;
  private final CoverageViewExtension myCoverageViewExtension;

  public CoverageViewTreeStructure(Project project, CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean) {
    myProject = project;
    myData = bundle;
    myStateBean = stateBean;
    myCoverageViewExtension = myData.getCoverageEngine().createCoverageViewExtension(project, bundle, stateBean);
    myRootNode = (CoverageListRootNode)myCoverageViewExtension.createRootNode();
  }


  @NotNull
  @Override
  public Object getRootElement() {
    return myRootNode;
  }

  @NotNull
  @Override
  public Object[] getChildElements(@NotNull final Object element) {
    return getChildren(element, myData, myStateBean);
  }

  static Object[] getChildren(Object element,
                              final CoverageSuitesBundle bundle,
                              CoverageViewManager.StateBean stateBean) {
    if (element instanceof CoverageListRootNode && stateBean.myFlattenPackages) {
      final Collection<? extends AbstractTreeNode> children = ((CoverageListRootNode)element).getChildren();
      return ArrayUtil.toObjectArray(children);
    }
    if (element instanceof CoverageListNode) {
      List<AbstractTreeNode> children = bundle.getCoverageEngine().createCoverageViewExtension(((CoverageListNode)element).getProject(),
                                                                                               bundle, stateBean)
        .getChildrenNodes((CoverageListNode)element);
      return children.toArray(new CoverageListNode[0]);
    }
    return null;
  }


  @Override
  public Object getParentElement(@NotNull final Object element) {
    final PsiElement psiElement = (PsiElement)element;
    return myCoverageViewExtension.getParentElement(psiElement);
  }

  @Override
  @NotNull
  public CoverageViewDescriptor createDescriptor(@NotNull final Object element, final NodeDescriptor parentDescriptor) {
    return new CoverageViewDescriptor(myProject, parentDescriptor, element);
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  public boolean supportFlattenPackages() {
    return myCoverageViewExtension.supportFlattenPackages();
  }
}

