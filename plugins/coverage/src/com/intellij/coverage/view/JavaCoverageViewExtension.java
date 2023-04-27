// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.CommonBundle;
import com.intellij.coverage.*;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.JavaCoverageEnabledConfiguration;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
  public String getSummaryForNode(@NotNull AbstractTreeNode node) {
    if (!myCoverageViewManager.isReady()) return CommonBundle.getLoadingTreeNodeText();
    if (myCoverageDataManager.isSubCoverageActive()) {
      return showSubCoverageNotification();
    }
    PsiPackage aPackage = (PsiPackage)node.getValue();
    final String coverageInformationString = myAnnotator
      .getPackageCoverageInformationString(aPackage, null, myCoverageDataManager, myStateBean.myFlattenPackages);
    return JavaCoverageBundle.message("coverage.view.node.summary", getNotCoveredMessage(coverageInformationString),
                                   aPackage != null ? aPackage.getQualifiedName() : node.getName());
  }

  private static @Nls String showSubCoverageNotification() {
    return JavaCoverageBundle.message("sub.coverage.notification");
  }

  @Override
  public String getSummaryForRootNode(@NotNull AbstractTreeNode childNode) {
    if (myCoverageDataManager.isSubCoverageActive()) {
      return showSubCoverageNotification();
    }
    final Object value = childNode.getValue();
    String coverageInformationString = myAnnotator.getPackageCoverageInformationString((PsiPackage)value, null,
                                                                                       myCoverageDataManager);
    if (coverageInformationString == null) {
      if (!myCoverageViewManager.isReady()) return CommonBundle.getLoadingTreeNodeText();
      PackageAnnotator.SummaryCoverageInfo info = new PackageAnnotator.PackageCoverageInfo();
      final Collection children = childNode.getChildren();
      for (Object child : children) {
        final Object childValue = ((CoverageListNode)child).getValue();
        PackageAnnotator.SummaryCoverageInfo childInfo = getSummaryCoverageForNodeValue((AbstractTreeNode<?>)childValue);
        info = JavaCoverageAnnotator.merge(info, childInfo);
      }
      coverageInformationString = JavaCoverageAnnotator.getCoverageInformationString(info, false);
    }
    return JavaCoverageBundle.message("coverage.view.root.node.summary", getNotCoveredMessage(coverageInformationString));
  }

  private static String getNotCoveredMessage(String coverageInformationString) {
    if (coverageInformationString == null) {
      coverageInformationString = JavaCoverageBundle.message("coverage.view.no.coverage");
    }
    return coverageInformationString;
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

  private PackageAnnotator.SummaryCoverageInfo getSummaryCoverageForNodeValue(AbstractTreeNode<?> node) {
    if (node instanceof CoverageListRootNode) {
      return myAnnotator.getPackageCoverageInfo("", myStateBean.myFlattenPackages);
    }
    final JavaCoverageNode javaNode = (JavaCoverageNode)node;
    if (javaNode.isLeaf()) {
      return myAnnotator.getClassCoverageInfo(javaNode.getQualifiedName());
    }
    else {
      return myAnnotator.getPackageCoverageInfo(javaNode.getQualifiedName(), myStateBean.myFlattenPackages);
    }
  }

  @Override
  public PsiElement getElementToSelect(Object object) {
    PsiElement psiElement = super.getElementToSelect(object);
    if (psiElement != null) {
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

  @NotNull
  @Override
  public List<AbstractTreeNode<?>> createTopLevelNodes() {
    final LinkedHashSet<PsiPackage> packages = new LinkedHashSet<>();
    final LinkedHashSet<PsiClass> classes = new LinkedHashSet<>();
    for (CoverageSuite suite : mySuitesBundle.getSuites()) {
      packages.addAll(((JavaCoverageSuite)suite).getCurrentSuitePackages(myProject));
      classes.addAll(((JavaCoverageSuite)suite).getCurrentSuiteClasses(myProject));
    }

    final Set<PsiPackage> packs = new HashSet<>();
    for (PsiPackage aPackage : packages) {
      final String qualifiedName = aPackage.getQualifiedName();
      for (PsiPackage psiPackage : packages) {
        if (psiPackage.getQualifiedName().startsWith(qualifiedName + ".")) {
          packs.add(psiPackage);
          break;
        }
      }
    }
    packages.removeAll(packs);

    final List<AbstractTreeNode<?>> topLevelNodes = new ArrayList<>();
    final GlobalSearchScope searchScope = mySuitesBundle.getSearchScope(myProject);
    for (PsiPackage aPackage : packages) {
      processSubPackage(aPackage, topLevelNodes, searchScope);
    }

    for (PsiClass aClass : classes) {
      final JavaCoverageNode node = new JavaCoverageNode(myProject, aClass, mySuitesBundle, myStateBean);
      node.setFullyCovered(isFullyCovered(aClass));
      topLevelNodes.add(node);
    }
    return topLevelNodes;
  }

  private void collectSubPackages(List<AbstractTreeNode<?>> children,
                                  final PsiPackage rootPackage,
                                  GlobalSearchScope searchScope) {
    final PsiPackage[] subPackages = getSubpackages(rootPackage, searchScope);
    for (final PsiPackage aPackage : subPackages) {
      processSubPackage(aPackage, children, searchScope);
    }
  }

  private void processSubPackage(final PsiPackage aPackage,
                                 List<AbstractTreeNode<?>> children,
                                 GlobalSearchScope searchScope) {
    if (shouldIncludePackage(aPackage, searchScope)) {
      final JavaCoverageNode node = new JavaCoverageNode(aPackage.getProject(), aPackage, mySuitesBundle, myStateBean);
      node.setFullyCovered(isFullyCovered(aPackage));
      children.add(node);
    }
    else if (!myStateBean.myFlattenPackages) {
      collectSubPackages(children, aPackage, searchScope);
    }
    if (myStateBean.myFlattenPackages) {
      collectSubPackages(children, aPackage, searchScope);
    }
  }

  private boolean shouldIncludePackage(PsiPackage aPackage, GlobalSearchScope searchScope) {
    return ReadAction.compute(() -> {
      if (!isInCoverageScope(aPackage)) return false;
      if (!myAnnotator.isLoading()) {
        final PackageAnnotator.PackageCoverageInfo info = getPackageCoverageInfo(aPackage);
        if (info == null) return false;
      }
      return !myStateBean.myFlattenPackages || aPackage.getClasses(searchScope).length != 0;
    });
  }

  private boolean shouldIncludeClass(PsiClass aClass) {
    if (!myAnnotator.isLoading()) {
      final PackageAnnotator.ClassCoverageInfo info = getClassCoverageInfo(aClass);
      if (info == null) return false;
    }
    return true;
  }

  private boolean isFullyCovered(PsiNamedElement classOrPackage) {
    final PackageAnnotator.SummaryCoverageInfo info;
    if (classOrPackage instanceof PsiPackage psiPackage) {
      info = getPackageCoverageInfo(psiPackage);
    }
    else if (classOrPackage instanceof PsiClass psiClass) {
      info = getClassCoverageInfo(psiClass);
    }
    else {
      return false;
    }
    return info != null && info.isFullyCovered();
  }

  @Override
  public List<AbstractTreeNode<?>> getChildrenNodes(final AbstractTreeNode node) {
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    if (node instanceof CoverageListNode) {
      final Object val = node.getValue();
      if (val instanceof PsiClass) return Collections.emptyList();

      //append package classes
      if (val instanceof PsiPackage psiPackage) {
        final GlobalSearchScope searchScope = mySuitesBundle.getSearchScope(myProject);
        if (ReadAction.compute(() -> isInCoverageScope(psiPackage))) {
          if (!myStateBean.myFlattenPackages) {
            collectSubPackages(children, psiPackage, searchScope);
          }

          final PsiFile[] childFiles = getFiles(psiPackage, searchScope);
          for (final PsiFile file : childFiles) {
            collectFileChildren(file, children);
          }
        }
        else if (!myStateBean.myFlattenPackages) {
          collectSubPackages(children, (PsiPackage)val, searchScope);
        }
      }
      if (node instanceof CoverageListRootNode) {
        for (CoverageSuite suite : mySuitesBundle.getSuites()) {
          final List<PsiClass> classes = ((JavaCoverageSuite)suite).getCurrentSuiteClasses(myProject);
          for (PsiClass aClass : classes) {
            final JavaCoverageNode classNode = new JavaCoverageNode(myProject, aClass, mySuitesBundle, myStateBean);
            classNode.setFullyCovered(isFullyCovered(aClass));
            children.add(classNode);
          }
        }
      }
      for (AbstractTreeNode<?> childNode : children) {
        childNode.setParent(node);
      }
    }
    return children;
  }

  private static PsiFile[] getFiles(PsiPackage psiPackage, GlobalSearchScope searchScope) {
    return ReadAction.compute(() -> psiPackage.isValid() ? psiPackage.getFiles(searchScope) : PsiFile.EMPTY_ARRAY);
  }

  private static PsiPackage[] getSubpackages(PsiPackage psiPackage, GlobalSearchScope searchScope) {
    return ReadAction.compute(() -> psiPackage.isValid() ? psiPackage.getSubPackages(searchScope) : PsiPackage.EMPTY_ARRAY);
  }

  protected void collectFileChildren(final PsiFile file, List<? super AbstractTreeNode<?>> children) {
    if (file instanceof PsiClassOwner) {
      PsiClass[] classes = ReadAction.compute(() -> file.isValid() ? ((PsiClassOwner)file).getClasses() : PsiClass.EMPTY_ARRAY);
      for (PsiClass aClass : classes) {
        if (shouldIncludeClass(aClass)) {
          final JavaCoverageNode node = new JavaCoverageNode(myProject, aClass, mySuitesBundle, myStateBean);
          node.setFullyCovered(isFullyCovered(aClass));
          children.add(node);
        }
      }
    }
  }

  @Nullable
  private PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(final PsiClass aClass) {
    return myAnnotator.getClassCoverageInfo(ReadAction.compute(() -> aClass.isValid() ? aClass.getQualifiedName() : null));
  }

  @Nullable
  private PackageAnnotator.PackageCoverageInfo getPackageCoverageInfo(final PsiPackage aPackage) {
    return ReadAction.compute(() -> myAnnotator.getPackageCoverageInfo(aPackage.getQualifiedName(), myStateBean.myFlattenPackages));
  }

  @Override
  public ColumnInfo[] createColumnInfos() {
    ArrayList<ColumnInfo> infos = new ArrayList<>();
    infos.add(new ElementColumnInfo());
    infos.add(new PercentageCoverageColumnInfo(1, JavaCoverageBundle.message("coverage.view.column.class"), mySuitesBundle, myStateBean));
    infos.add(new PercentageCoverageColumnInfo(2, JavaCoverageBundle.message("coverage.view.column.method"), mySuitesBundle, myStateBean));
    infos.add(new PercentageCoverageColumnInfo(3, JavaCoverageBundle.message("coverage.view.column.line"), mySuitesBundle, myStateBean));
    RunConfigurationBase<?> runConfiguration = mySuitesBundle.getRunConfiguration();
    if (runConfiguration != null) {
      JavaCoverageEnabledConfiguration coverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(runConfiguration);
      if (coverageEnabledConfiguration != null) {
        tryAddBranches(infos, coverageEnabledConfiguration.getCoverageRunner(), coverageEnabledConfiguration.isTracingEnabled());
      }
    }
    else {
      for (CoverageSuite suite : mySuitesBundle.getSuites()) {
        CoverageRunner runner = suite.getRunner();
        if (tryAddBranches(infos, runner, false)) {
          break;
        }
      }
    }
    return infos.toArray(ColumnInfo.EMPTY_ARRAY);
  }

  private boolean tryAddBranches(ArrayList<? super ColumnInfo> infos, CoverageRunner coverageRunner, boolean branchCoverage) {
    if (isBranchInfoAvailable(coverageRunner, branchCoverage)) {
      infos.add(new PercentageCoverageColumnInfo(4, JavaCoverageBundle.message("coverage.view.column.branch"), mySuitesBundle, myStateBean));
      return true;
    }
    return false;
  }

  protected boolean isBranchInfoAvailable(CoverageRunner coverageRunner, boolean branchCoverage) {
    return coverageRunner instanceof JavaCoverageRunner && ((JavaCoverageRunner)coverageRunner).isBranchInfoAvailable(branchCoverage);
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
