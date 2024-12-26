// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public abstract class CoverageViewExtension {
  protected final Project myProject;
  protected final CoverageSuitesBundle mySuitesBundle;
  protected final CoverageViewManager.StateBean myStateBean;
  protected final CoverageDataManager myCoverageDataManager;

  /**
   * @deprecated Use {@link CoverageViewExtension#CoverageViewExtension(Project, CoverageSuitesBundle)}
   */
  @Deprecated
  public CoverageViewExtension(@NotNull Project project,
                               CoverageSuitesBundle suitesBundle,
                               @SuppressWarnings("unused") CoverageViewManager.StateBean stateBean) {
    this(project, suitesBundle);
  }

  public CoverageViewExtension(@NotNull Project project, CoverageSuitesBundle suitesBundle) {
    assert !project.isDefault() : "Should not run coverage for default project";
    myProject = project;
    mySuitesBundle = suitesBundle;
    myStateBean = CoverageViewManager.getInstance(myProject).getStateBean();
    myCoverageDataManager = CoverageDataManager.getInstance(myProject);
  }

  public abstract @Nullable String getPercentage(int columnIdx, @NotNull AbstractTreeNode<?> node);

  public abstract @Unmodifiable List<AbstractTreeNode<?>> getChildrenNodes(AbstractTreeNode<?> node);

  public abstract ColumnInfo[] createColumnInfos();

  public abstract @Nullable PsiElement getParentElement(PsiElement element);

  public abstract @NotNull AbstractTreeNode<?> createRootNode();

  void onRootReset() {
  }

  @ApiStatus.Internal
  public boolean hasChildren(AbstractTreeNode<?> node) {
    return !node.getChildren().isEmpty();
  }

  @ApiStatus.Internal
  public boolean hasFullyCoveredNodes() {
    return false;
  }

  public boolean canSelectInCoverageView(Object object) {
    return object instanceof VirtualFile && PsiManager.getInstance(myProject).findFile((VirtualFile)object) != null;
  }

  @ApiStatus.Internal
  public @Nullable PsiElement getElementToSelect(Object object) {
    if (object instanceof PsiElement) return (PsiElement)object;
    return object instanceof VirtualFile ? PsiManager.getInstance(myProject).findFile((VirtualFile)object) : null;
  }

  @ApiStatus.Internal
  protected @Nullable VirtualFile getVirtualFile(Object object) {
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

  @ApiStatus.Internal
  public boolean supportFlattenPackages() {
    return false;
  }

  /**
   * @return extra actions which will be added to {@link CoverageView} toolbar menu
   * directly after all the default actions (Flatten Packages, Generate Coverage Report)
   */
  @ApiStatus.Experimental
  public @NotNull List<AnAction> createExtraToolbarActions() {
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
  @Deprecated(forRemoval = true)
  public @NotNull List<AbstractTreeNode<?>> createTopLevelNodes() {
    return Collections.emptyList();
  }

  /**
   * @deprecated This method is not used in CoverageView.
   */
  @Deprecated
  public @Nullable String getSummaryForNode(@NotNull AbstractTreeNode<?> ignoredNode) {
    return null;
  }

  /**
   * @deprecated This method is not used in CoverageView.
   */
  @Deprecated
  public @Nullable String getSummaryForRootNode(@NotNull AbstractTreeNode<?> ignoredNode) {
    return null;
  }
}