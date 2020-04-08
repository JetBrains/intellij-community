// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class CoverageViewExtension {
  protected final Project myProject;
  protected final CoverageSuitesBundle mySuitesBundle;
  protected final CoverageViewManager.StateBean myStateBean;
  protected final CoverageDataManager myCoverageDataManager;
  protected final CoverageViewManager myCoverageViewManager;

  public CoverageViewExtension(@NotNull Project project, CoverageSuitesBundle suitesBundle, CoverageViewManager.StateBean stateBean) {
    assert !project.isDefault() : "Should not run coverage for default project";
    myProject = project;
    mySuitesBundle = suitesBundle;
    myStateBean = stateBean;
    myCoverageDataManager = CoverageDataManager.getInstance(myProject);
    myCoverageViewManager = CoverageViewManager.getInstance(myProject);
  }

  @Nullable
  public abstract String getSummaryForNode(AbstractTreeNode<?> node);

  @Nullable
  public abstract String getSummaryForRootNode(AbstractTreeNode<?> childNode);

  @Nullable
  public abstract String getPercentage(int columnIdx, AbstractTreeNode<?> node);

  public abstract List<AbstractTreeNode<?>> getChildrenNodes(AbstractTreeNode<?> node);

  public abstract ColumnInfo[] createColumnInfos();

  @Nullable
  public abstract PsiElement getParentElement(PsiElement element);

  @NotNull
  public abstract AbstractTreeNode<?> createRootNode();

  public boolean canSelectInCoverageView(Object object) {
    return object instanceof VirtualFile && PsiManager.getInstance(myProject).findFile((VirtualFile)object) != null;
  }

  @Nullable
  public PsiElement getElementToSelect(Object object) {
    if (object instanceof PsiElement) return (PsiElement)object;
    return object instanceof VirtualFile ? PsiManager.getInstance(myProject).findFile((VirtualFile)object) : null;
  }

  @Nullable
  public VirtualFile getVirtualFile(Object object) {
    if (object instanceof PsiElement) {
      if (object instanceof PsiDirectory) return ((PsiDirectory)object).getVirtualFile();
      final PsiFile containingFile = ((PsiElement)object).getContainingFile();
      if (containingFile != null) {
        return containingFile.getVirtualFile();
      }
      return null;
    }
    return object instanceof VirtualFile ? (VirtualFile)object : null;
  }

  @NotNull
  public List<AbstractTreeNode<?>> createTopLevelNodes() {
    return Collections.emptyList();
  }

  public boolean supportFlattenPackages() {
    return false;
  }
}