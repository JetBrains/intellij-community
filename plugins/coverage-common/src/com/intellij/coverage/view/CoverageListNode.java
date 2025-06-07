// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final FileStatusManager myFileStatusManager;
  private final VirtualFile myFile;

  /**
   * @deprecated Use {@link CoverageViewManager#getStateBean()}
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  protected final CoverageViewManager.StateBean myStateBean;

  /**
   * Children are cached in order to be able to filter nodes with no (interesting) children.
   * @see DirectoryCoverageViewExtension#getChildrenNodes(AbstractTreeNode)
   */
  private List<AbstractTreeNode<?>> myCachedChildren;

  /**
   * @deprecated Use {@link CoverageListNode#CoverageListNode(Project, PsiNamedElement, CoverageSuitesBundle)}
   */
  @Deprecated
  public CoverageListNode(Project project,
                          @NotNull PsiNamedElement element,
                          CoverageSuitesBundle bundle,
                          @SuppressWarnings("unused") CoverageViewManager.StateBean stateBean,
                          @SuppressWarnings("unused") boolean unused) {
    this(project, element, bundle);
  }

  /**
   * @deprecated Use {@link CoverageListNode#CoverageListNode(Project, PsiNamedElement, CoverageSuitesBundle)}
   */
  @Deprecated
  public CoverageListNode(Project project,
                          @NotNull PsiNamedElement element,
                          CoverageSuitesBundle bundle,
                          @SuppressWarnings("unused") CoverageViewManager.StateBean stateBean) {
    this(project, element, bundle);
  }

  public CoverageListNode(Project project,
                          @NotNull PsiNamedElement element,
                          CoverageSuitesBundle bundle) {
    super(project, element);

    myName = ReadAction.compute(() -> element.getName());
    myBundle = bundle;
    myStateBean = CoverageViewManager.getInstance(project).getStateBean();
    myFileStatusManager = FileStatusManager.getInstance(myProject);
    myFile = ReadAction.compute(() -> {
      VirtualFile file = element.isValid() ? PsiUtilCore.getVirtualFile(element) : null;
      if (file != null) {
        VirtualFile canonical = file.getCanonicalFile();
        if (canonical != null) return canonical;
      }
      return file;
    });
  }

  @Override
  public synchronized @NotNull List<? extends AbstractTreeNode<?>> getChildren() {
    if (myCachedChildren != null) return myCachedChildren;
    return myCachedChildren = myBundle.getCoverageEngine().createCoverageViewExtension(myProject, myBundle)
      .getChildrenNodes(this);
  }

  @Override
  protected void update(final @NotNull PresentationData presentation) {
    ApplicationManager.getApplication().runReadAction(() -> {
      final Object object = getValue();
      if (object instanceof PsiNamedElement value) {
        CoverageViewManager.StateBean stateBean = CoverageViewManager.getInstance(myProject).getStateBean();
        if (value instanceof PsiQualifiedNamedElement &&
            (stateBean.isFlattenPackages() && value.getContainingFile() == null || getParent() instanceof CoverageListRootNode)) {
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

  VirtualFile getFile() {
    return myFile;
  }

  @Override
  public FileStatus getFileStatus() {
    VirtualFile virtualFile = myFile;
    return virtualFile != null ? myFileStatusManager.getStatus(virtualFile) : super.getFileStatus();
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
    return value instanceof PsiElement element && element.isValid() && element.getContainingFile() != null;
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
      final PsiNamedElement value = (PsiNamedElement)getValue();
      if (requestFocus && value != null) {
        NavigationUtil.activateFileWithPsiElement(value, true);
      }
      else if (value instanceof NavigationItem navigationItem) {
        navigationItem.navigate(false);
      }
    }
  }

  @Override
  public int getWeight() {
    return ReadAction.compute(() -> {
      //todo weighted
      final Object value = getValue();
      if (value instanceof PsiElement element && element.getContainingFile() != null) return 40;
      return 30;
    });
  }

  boolean contains(VirtualFile file) {
    final Object value = getValue();
    if (value instanceof PsiElement element) {
      final boolean equalContainingFile = Comparing.equal(PsiUtilCore.getVirtualFile(element), file);
      if (equalContainingFile) return true;
    }
    if (value instanceof PsiDirectory directory) {
      return contains(file, directory);
    }
    else if (value instanceof PsiDirectoryContainer container) {
      final PsiDirectory[] directories = container.getDirectories();
      for (PsiDirectory directory : directories) {
        if (contains(file, directory)) return true;
      }
    }
    return false;
  }

  private boolean contains(VirtualFile file, PsiDirectory value) {
    CoverageViewManager.StateBean stateBean = CoverageViewManager.getInstance(myProject).getStateBean();
    if (stateBean.isFlattenPackages()) {
      return Comparing.equal(value.getVirtualFile(), file.getParent());
    }

    if (VfsUtilCore.isAncestor(value.getVirtualFile(), file, false)) {
      return true;
    }

    return false;
  }
}
