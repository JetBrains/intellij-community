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
  protected CoverageSuitesBundle myBundle;
  protected CoverageViewManager.StateBean myStateBean;
  private final FileStatusManager myFileStatusManager;

  public CoverageListNode(Project project,
                          @NotNull PsiNamedElement classOrPackage,
                          CoverageSuitesBundle bundle,
                          CoverageViewManager.StateBean stateBean) {
    super(project, classOrPackage);

    myName = ReadAction.compute(() -> classOrPackage.getName());
    myBundle = bundle;
    myStateBean = stateBean;
    myFileStatusManager = FileStatusManager.getInstance(myProject);
  }

  @NotNull
  @Override
  public List<? extends AbstractTreeNode<?>> getChildren() {
    return myBundle.getCoverageEngine().createCoverageViewExtension(myProject, myBundle, myStateBean)
      .getChildrenNodes(this);
  }

  @Override
  protected void update(@NotNull final PresentationData presentation) {
    ApplicationManager.getApplication().runReadAction(() -> {
      final Object object = getValue();
      if (object instanceof PsiNamedElement) {
        final PsiNamedElement value = (PsiNamedElement)object;
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
