// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class CoverageViewExtension {
  protected final Project myProject;
  protected final CoverageSuitesBundle mySuitesBundle;
  protected final CoverageViewManager.StateBean myStateBean;
  protected final CoverageDataManager myCoverageDataManager;

  public CoverageViewExtension(@NotNull Project project, CoverageSuitesBundle suitesBundle, CoverageViewManager.StateBean stateBean) {
    assert !project.isDefault() : "Should not run coverage for default project";
    myProject = project;
    mySuitesBundle = suitesBundle;
    myStateBean = stateBean;
    myCoverageDataManager = CoverageDataManager.getInstance(myProject);
  }

  @Nullable
  public abstract String getPercentage(int columnIdx, @NotNull AbstractTreeNode<?> node);

  public abstract List<AbstractTreeNode<?>> getChildrenNodes(AbstractTreeNode<?> node);

  public abstract ColumnInfo[] createColumnInfos();

  @Nullable
  public abstract PsiElement getParentElement(PsiElement element);

  @NotNull
  public abstract AbstractTreeNode<?> createRootNode();

  public boolean hasChildren(AbstractTreeNode<?> node) {
    return !getChildrenNodes(node).isEmpty();
  }

  public boolean hasVCSFilteredNodes() {
    return false;
  }

  public boolean hasFullyCoveredNodes() {
    return false;
  }

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

  public boolean supportFlattenPackages() {
    return false;
  }

  /**
   * @return extra actions which will be added to {@link CoverageView} toolbar menu
   * directly after all the default actions (Flatten Packages, Generate Coverage Report)
   */
  @ApiStatus.Experimental
  @NotNull
  public List<AnAction> createExtraToolbarActions() {
    return Collections.emptyList();
  }

  public String getElementsName() {
    return CoverageBundle.message("coverage.files");
  }

  public String getElementsCapitalisedName() {
    return CoverageBundle.message("coverage.files.capitalised");
  }

  /**
   * @deprecated This method is not used in CoverageView.
   * The root node should return a correct list of children instead.
   */
  @NotNull
  @Deprecated
  public List<AbstractTreeNode<?>> createTopLevelNodes() {
    return Collections.emptyList();
  }

  /**
   * @deprecated This method is not used in CoverageView.
   */
  @Nullable
  @Deprecated
  public String getSummaryForNode(@NotNull AbstractTreeNode<?> ignoredNode) {
    return null;
  }

  /**
   * @deprecated This method is not used in CoverageView.
   */
  @Nullable
  @Deprecated
  public String getSummaryForRootNode(@NotNull AbstractTreeNode<?> ignoredNode) {
    return null;
  }


  public static boolean isModified(FileStatus status) {
    return status == FileStatus.MODIFIED || status == FileStatus.ADDED || status == FileStatus.UNKNOWN;
  }
}