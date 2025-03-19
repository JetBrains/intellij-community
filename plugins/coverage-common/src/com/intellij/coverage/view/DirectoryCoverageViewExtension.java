// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageAnnotator;
import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.filters.ModifiedFilesFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public class DirectoryCoverageViewExtension extends CoverageViewExtension {
  protected final CoverageAnnotator myAnnotator;

  /**
   * @deprecated Use {@link DirectoryCoverageViewExtension#DirectoryCoverageViewExtension(Project, CoverageAnnotator, CoverageSuitesBundle)}
   */
  @Deprecated
  public DirectoryCoverageViewExtension(Project project,
                                        CoverageAnnotator annotator,
                                        CoverageSuitesBundle suitesBundle,
                                        @SuppressWarnings("unused") CoverageViewManager.StateBean stateBean) {
    this(project, annotator, suitesBundle);
  }

  public DirectoryCoverageViewExtension(Project project,
                                        CoverageAnnotator annotator,
                                        CoverageSuitesBundle suitesBundle) {
    super(project, suitesBundle);
    myAnnotator = annotator;
  }

  @Override
  public ColumnInfo[] createColumnInfos() {
    return new ColumnInfo[]{new ElementColumnInfo(),
      new PercentageCoverageColumnInfo(1, CoverageBundle.message("table.column.name.statistics"), mySuitesBundle)};
  }

  @Override
  public String getPercentage(int columnIdx, @NotNull AbstractTreeNode<?> node) {
    VirtualFile file = extractFile(node);
    if (file == null) return null;
    if (file.isDirectory()) {
      return myAnnotator.getDirCoverageInformationString(myProject, file, mySuitesBundle, myCoverageDataManager);
    }
    else {
      return myAnnotator.getFileCoverageInformationString(myProject, file, mySuitesBundle, myCoverageDataManager);
    }
  }

  protected @Nullable VirtualFile extractFile(@NotNull AbstractTreeNode<?> node) {
    if (node instanceof CoverageListNode coverageNode) {
      return coverageNode.getFile();
    }
    return null;
  }


  @Override
  public PsiElement getParentElement(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      return containingFile.getContainingDirectory();
    }
    return null;
  }

  @Override
  public @NotNull AbstractTreeNode<?> createRootNode() {
    VirtualFile baseDir = ProjectUtil.guessProjectDir(myProject);
    if (baseDir == null) {
      final VirtualFile[] roots = ProjectRootManager.getInstance(myProject).getContentRoots();
      baseDir = VfsUtil.getCommonAncestor(Arrays.asList(roots));
    }
    PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(Objects.requireNonNull(baseDir));
    return new CoverageListRootNode(myProject, Objects.requireNonNull(directory), mySuitesBundle);
  }

  @Override
  void onRootReset() {
    ModifiedFilesFilter filter = myAnnotator.getModifiedFilesFilter();
    if (filter != null) {
      filter.resetFilteredFiles();
    }
  }

  @Override
  public @Unmodifiable List<AbstractTreeNode<?>> getChildrenNodes(AbstractTreeNode node) {
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    if (node instanceof CoverageListNode) {
      final Object val = node.getValue();
      if (val instanceof PsiFile || val == null) return Collections.emptyList();
      final PsiDirectory psiDirectory = (PsiDirectory)val;
      final PsiDirectory[] subdirectories = ReadAction.compute(() -> psiDirectory.getSubdirectories());
      for (PsiDirectory subdirectory : subdirectories) {
        if (myAnnotator.getDirCoverageInformationString(subdirectory, mySuitesBundle, myCoverageDataManager) == null) continue;
        CoverageListNode e = new CoverageListNode(myProject, subdirectory, mySuitesBundle);
        if (!e.getChildren().isEmpty()) {
          children.add(e);
        }
      }
      final PsiFile[] psiFiles = ReadAction.compute(() -> psiDirectory.getFiles());
      for (PsiFile psiFile : psiFiles) {
        if (myAnnotator.getFileCoverageInformationString(psiFile, mySuitesBundle, myCoverageDataManager) == null) continue;
        CoverageListNode e = new CoverageListNode(myProject, psiFile, mySuitesBundle);
        VirtualFile file = e.getFile();
        CoverageViewManager.StateBean stateBean = CoverageViewManager.getInstance(myProject).getStateBean();
        ModifiedFilesFilter filter = myAnnotator.getModifiedFilesFilter();
        if (!stateBean.isShowOnlyModified() || file == null || filter == null || filter.isFileModified(file)) {
          children.add(e);
        }
      }
    }
    return children;
  }
}
