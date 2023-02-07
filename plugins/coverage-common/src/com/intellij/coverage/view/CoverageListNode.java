// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class CoverageListNode extends AbstractTreeNode<Object> {
  protected final CoverageSuitesBundle myBundle;
  protected final CoverageViewManager.StateBean myStateBean;
  private final FileStatusManager myFileStatusManager;
  private volatile List<AbstractTreeNode<?>> myChildren;
  private final boolean myIsLeaf;
  private boolean myFullyCovered = false;

  public CoverageListNode(Project project,
                          @NotNull PsiNamedElement classOrPackage,
                          CoverageSuitesBundle bundle,
                          CoverageViewManager.StateBean stateBean) {
    this(project, classOrPackage, bundle, stateBean, false);
  }

  public CoverageListNode(Project project,
                          @NotNull PsiNamedElement classOrPackage,
                          CoverageSuitesBundle bundle,
                          CoverageViewManager.StateBean stateBean,
                          boolean isLeaf) {
    super(project, classOrPackage);

    myName = ReadAction.compute(() -> classOrPackage.getName());
    myBundle = bundle;
    myStateBean = stateBean;
    myFileStatusManager = FileStatusManager.getInstance(myProject);
    myIsLeaf = isLeaf;
  }

  public synchronized void reset() {
    myChildren = null;
  }

  public boolean isLeaf() {
    return myIsLeaf;
  }

  protected boolean isFullyCovered() {
    return myFullyCovered;
  }

  public void setFullyCovered(boolean value) {
    myFullyCovered = value;
  }

  private CoverageListRootNode myRoot;

  CoverageListRootNode getRoot() {
    if (myRoot == null) {
      var parent = (CoverageListNode)getParent();
      if (parent == null) {
        if (this instanceof CoverageListRootNode root) {
          myRoot = root;
        }
        else {
          throw new RuntimeException("Coverage node unexpectedly has no parent " + this +
                                     ". Each coverage node is supposed to have a parent or to be CoverageListRootNode instance.");
        }
      }
      else {
        myRoot = parent.getRoot();
      }
    }
    return myRoot;
  }

  @NotNull
  @Override
  public List<? extends AbstractTreeNode<?>> getChildren() {
    return getChildrenInternal();
  }

  private synchronized List<? extends AbstractTreeNode<?>> getChildrenInternal() {
    if (myChildren == null) {
      final var nodes = myBundle.getCoverageEngine().createCoverageViewExtension(myProject, myBundle, myStateBean)
        .getChildrenNodes(this);
      myChildren = filterChildren(nodes);
    }
    return myChildren;
  }

  protected List<AbstractTreeNode<?>> filterChildren(List<AbstractTreeNode<?>> nodes) {
    if (myStateBean.isShowOnlyModified() || myStateBean.isHideFullyCovered()) {
      nodes = nodes.stream().filter((node) -> {
        boolean filtered = true;
        boolean isLeaf = false;
        if (node instanceof CoverageListNode coverageNode) {
          isLeaf = coverageNode.isLeaf();
          final boolean fullyCovered = coverageNode.isFullyCovered();
          if (myStateBean.isHideFullyCovered() && fullyCovered) {
            filtered = false;
            getRoot().setHasFullyCoveredChildren(true);
          }
        }
        if (myStateBean.isShowOnlyModified() && isLeaf) {
          final FileStatus status = node.getFileStatus();
          final boolean isModified = status == FileStatus.MODIFIED || status == FileStatus.ADDED || status == FileStatus.UNKNOWN;
          if (!isModified) {
            filtered = false;
            getRoot().setHasVCSFilteredChildren(true);
          }
        }
        return filtered;
      }).toList();
    }

    return nodes.stream().filter((node) -> {
      if (node instanceof CoverageListNode) {
        if (((CoverageListNode)node).isLeaf()) return true;
      }
      return !node.getChildren().isEmpty();
    }).toList();
  }

  @Override
  protected void update(@NotNull final PresentationData presentation) {
    ApplicationManager.getApplication().runReadAction(() -> {
      final Object object = getValue();
      if (object instanceof PsiNamedElement value) {
        if (value instanceof PsiQualifiedNamedElement &&
            (myStateBean.myFlattenPackages && value.getContainingFile() == null || getParent() instanceof CoverageListRootNode)) {
          presentation.setPresentableText(((PsiQualifiedNamedElement)value).getQualifiedName());
        }
        else {
          presentation.setPresentableText(value.getName());
        }
        presentation.setIcon(value.getIcon(0));
        presentation.setForcedTextForeground(getFileStatus().getColor());
      }
    });
  }

  @Override
  public FileStatus getFileStatus() {
    final PsiFile containingFile = ReadAction.compute(() -> {
      Object value = getValue();
      if (value instanceof PsiElement && ((PsiElement)value).isValid()) {
        return ((PsiElement)value).getContainingFile();
      }
      return null;
    });
    return containingFile != null ? myFileStatusManager.getStatus(containingFile.getVirtualFile()) : super.getFileStatus();
  }

  @Override
  protected @Nullable Color computeBackgroundColor() {
    return null;
  }

  @Override
  protected boolean shouldPostprocess() {
    return false;
  }

  @Override
  public boolean canNavigate() {
    final Object value = getValue();
    return value instanceof PsiElement && ((PsiElement)value).isValid() && ((PsiElement)value).getContainingFile() != null;
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
        ((NavigationItem)value).navigate(false);
      }
    }
  }

  @Override
  public int getWeight() {
    return ReadAction.compute(() -> {
      //todo weighted
      final Object value = getValue();
      if (value instanceof PsiElement && ((PsiElement)value).getContainingFile() != null) return 40;
      return 30;
    });
  }

  public boolean contains(VirtualFile file) {
    final Object value = getValue();
    if (value instanceof PsiElement) {
      final boolean equalContainingFile = Comparing.equal(PsiUtilCore.getVirtualFile((PsiElement)value), file);
      if (equalContainingFile) return true;
    }
    if (value instanceof PsiDirectory) {
      return contains(file, (PsiDirectory)value);
    }
    else if (value instanceof PsiDirectoryContainer) {
      final PsiDirectory[] directories = ((PsiDirectoryContainer)value).getDirectories();
      for (PsiDirectory directory : directories) {
        if (contains(file, directory)) return true;
      }
    }
    return false;
  }

  private boolean contains(VirtualFile file, PsiDirectory value) {
    if (myStateBean.myFlattenPackages) {
      return Comparing.equal(value.getVirtualFile(), file.getParent());
    }

    if (VfsUtilCore.isAncestor(value.getVirtualFile(), file, false)) {
      return true;
    }

    return false;
  }
}
