package com.intellij.coverage.view;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
* User: anna
* Date: 1/2/12
*/
class CoverageListNode extends AbstractTreeNode {
  private CoverageSuitesBundle myBundle;
  private final Map<String, List<String>> myPackages;
  private List<AbstractTreeNode> myTopLevelPackages;
  private CoverageViewManager.StateBean myStateBean;

  public CoverageListNode(PsiNamedElement classOrPackage,
                          CoverageSuitesBundle bundle,
                          Map<String, List<String>> packages,
                          List<AbstractTreeNode> topLevelPackages,
                          CoverageViewManager.StateBean stateBean) {
    super(classOrPackage.getProject(), classOrPackage);
    myName = classOrPackage.getName();
    myBundle = bundle;
    myPackages = packages;
    myTopLevelPackages = topLevelPackages;
    myStateBean = stateBean;
  }

  public CoverageSuitesBundle getBundle() {
    return myBundle;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    return (Collection<CoverageListNode>)Arrays.asList(
      (CoverageListNode[])CoverageViewTreeStructure.getChildren(getValue(), myBundle, myPackages, myTopLevelPackages, myStateBean));
  }

  @Override
  protected void update(PresentationData presentation) {
    final Object value = getValue();
    if (value instanceof PsiElement) {
      if (value instanceof PsiClass) {
        presentation.setPresentableText(((PsiClass)value).getName());
      }
      else {
        presentation.setPresentableText(((PsiPackage)value).getQualifiedName());
      }
      presentation.setIcons(((PsiElement)value).getIcon(Iconable.ICON_FLAG_CLOSED));
    }
  }

  @Override
  public boolean canNavigate() {
    return getValue() instanceof PsiClass;
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
      final PsiNamedElement value = (PsiNamedElement)getValue();
      if (requestFocus) {
        NavigationUtil.activateFileWithPsiElement(value, true);
      }
      else if (value instanceof NavigationItem) {
        ((NavigationItem)value).navigate(requestFocus);
      }
    }
  }

  public boolean contains(VirtualFile file) {
    final Object value = getValue();
    if (value instanceof PsiClass) {
      return PsiUtilCore.getVirtualFile((PsiElement)value) == file;
    }
    final PsiDirectory[] directories = ((PsiPackage)value).getDirectories();
    for (PsiDirectory directory : directories) {
      if (VfsUtilCore.isAncestor(directory.getVirtualFile(), file, false)){
        return true;
      }
    }
    return false;
  }
}
