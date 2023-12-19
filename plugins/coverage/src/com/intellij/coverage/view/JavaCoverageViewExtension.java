// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.*;
import com.intellij.coverage.analysis.JavaCoverageAnnotator;
import com.intellij.coverage.analysis.PackageAnnotator;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JavaCoverageViewExtension extends CoverageViewExtension {
  private final JavaCoverageAnnotator myAnnotator;

  public JavaCoverageViewExtension(JavaCoverageAnnotator annotator,
                                   Project project,
                                   CoverageSuitesBundle suitesBundle,
                                   CoverageViewManager.StateBean stateBean) {
    super(project, suitesBundle, stateBean);
    myAnnotator = annotator;
  }

  @Override
  public String getPercentage(int columnIndex, @NotNull AbstractTreeNode node) {
    final PackageAnnotator.SummaryCoverageInfo info = getSummaryCoverageForNodeValue(node);

    if (columnIndex == 1) {
      return JavaCoverageAnnotator.getClassCoveredPercentage(info);
    }
    if (columnIndex == 2) {
      return JavaCoverageAnnotator.getMethodCoveredPercentage(info);
    }

    if (columnIndex == 3) {
      return JavaCoverageAnnotator.getLineCoveredPercentage(info);
    }

    if (columnIndex == 4) {
      return JavaCoverageAnnotator.getBranchCoveredPercentage(info);
    }
    return "";
  }

  @Nullable
  private PackageAnnotator.SummaryCoverageInfo getSummaryCoverageForNodeValue(AbstractTreeNode<?> node) {
    CoverageClassStructure structure = myAnnotator.getStructure();
    if (structure == null) return null;
    CoverageNodeInfo info = structure.getNodeInfo(getNodeId(node));
    if (info == null) return null;
    return info.getCounter();
  }

  @NotNull
  private static String getNodeId(AbstractTreeNode<?> node) {
    return node instanceof CoverageListRootNode ? "" : ((JavaCoverageNode)node).getQualifiedName();
  }

  @Override
  public PsiElement getElementToSelect(Object object) {
    PsiElement psiElement = super.getElementToSelect(object);
    if (psiElement != null && psiElement.isValid()) {
      final PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if (classes.length == 1) return classes[0];
        for (PsiClass aClass : classes) {
          if (PsiTreeUtil.isAncestor(aClass, psiElement, false)) return aClass;
        }
      }
    }
    return psiElement;
  }

  @Override
  public VirtualFile getVirtualFile(Object object) {
    if (object instanceof PsiPackage) {
      final PsiDirectory[] directories = ((PsiPackage)object).getDirectories();
      return directories.length > 0 ? directories[0].getVirtualFile() : null;
    }
    return super.getVirtualFile(object);
  }

  @Nullable
  @Override
  public PsiElement getParentElement(PsiElement element) {
    if (element instanceof PsiClass) {
      final PsiDirectory containingDirectory = element.getContainingFile().getContainingDirectory();
      return containingDirectory != null ? JavaDirectoryService.getInstance().getPackage(containingDirectory) : null;
    }
    return ((PsiPackage)element).getParentPackage();
  }

  @NotNull
  @Override
  public AbstractTreeNode<?> createRootNode() {
    final PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
    return new JavaCoverageRootNode(myProject, Objects.requireNonNull(aPackage), mySuitesBundle, myStateBean);
  }

  @Override
  public List<AbstractTreeNode<?>> getChildrenNodes(final AbstractTreeNode node) {
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    CoverageClassStructure structure = myAnnotator.getStructure();
    if (structure == null) return children;
    List<CoverageNodeInfo> infos = structure.getChildrenInfo(getNodeId(node));
    for (CoverageNodeInfo info : infos) {
      children.add(new JavaCoverageNode(myProject, info.getValue(), mySuitesBundle, myStateBean, info.getName()));
    }
    return children;
  }

  @Override
  public boolean hasFullyCoveredNodes() {
    CoverageClassStructure structure = myAnnotator.getStructure();
    if (structure == null) return false;
    return structure.getHasFullyCoveredChildren();
  }

  @Override
  public boolean hasVCSFilteredNodes() {
    CoverageClassStructure structure = myAnnotator.getStructure();
    if (structure == null) return false;
    return structure.getHasVCSFilteredChildren();
  }

  @Override
  public ColumnInfo[] createColumnInfos() {
    ArrayList<ColumnInfo> infos = new ArrayList<>();
    infos.add(new ElementColumnInfo());
    infos.add(new PercentageCoverageColumnInfo(1, JavaCoverageBundle.message("coverage.view.column.class"), mySuitesBundle, myStateBean));
    infos.add(new PercentageCoverageColumnInfo(2, JavaCoverageBundle.message("coverage.view.column.method"), mySuitesBundle, myStateBean));
    infos.add(new PercentageCoverageColumnInfo(3, JavaCoverageBundle.message("coverage.view.column.line"), mySuitesBundle, myStateBean));
    for (CoverageSuite suite : mySuitesBundle.getSuites()) {
      if (tryAddBranches(infos, suite.getRunner(), suite.isBranchCoverage())) {
        break;
      }
    }
    return infos.toArray(ColumnInfo.EMPTY_ARRAY);
  }

  private boolean tryAddBranches(ArrayList<? super ColumnInfo> infos, CoverageRunner coverageRunner, boolean branchCoverage) {
    // cannot determine per-test branch coverage as test tracking works with line granularity only
    if (CoverageDataManager.getInstance(myProject).isSubCoverageActive()) return false;
    if (isBranchInfoAvailable(coverageRunner, branchCoverage)) {
      infos.add(new PercentageCoverageColumnInfo(4, JavaCoverageBundle.message("coverage.view.column.branch"), mySuitesBundle, myStateBean));
      return true;
    }
    return false;
  }

  protected boolean isBranchInfoAvailable(CoverageRunner coverageRunner, boolean branchCoverage) {
    return coverageRunner instanceof JavaCoverageRunner javaCoverageRunner && javaCoverageRunner.isBranchInfoAvailable(branchCoverage);
  }

  private boolean isInCoverageScope(PsiElement element) {
    if (element instanceof PsiPackage psiPackage) {
      final String qualifiedName = psiPackage.getQualifiedName();
      for (CoverageSuite suite : mySuitesBundle.getSuites()) {
        if (((JavaCoverageSuite)suite).isPackageFiltered(qualifiedName)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean canSelectInCoverageView(Object object) {
    final PsiFile psiFile = object instanceof VirtualFile ? PsiManager.getInstance(myProject).findFile((VirtualFile)object) : null;
    if (psiFile instanceof PsiClassOwner) {
      final String packageName = ((PsiClassOwner)psiFile).getPackageName();
      return isInCoverageScope(JavaPsiFacade.getInstance(myProject).findPackage(packageName));
    }
    if (object instanceof PsiPackage) {
      return isInCoverageScope((PsiElement)object);
    }
    return false;
  }

  @Override
  public boolean supportFlattenPackages() {
    return true;
  }

  @Override
  public String getElementsName() {
    return JavaCoverageBundle.message("coverage.classes");
  }

  @Override
  public String getElementsCapitalisedName() {
    return JavaCoverageBundle.message("coverage.classes.capitalised");
  }
}
