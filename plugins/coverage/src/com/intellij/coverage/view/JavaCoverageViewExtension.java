/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.coverage.view;

import com.intellij.coverage.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: 1/5/12
 */
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
  public String getSummaryForNode(AbstractTreeNode node) {
    if (!myCoverageViewManager.isReady()) return "Loading...";
    final String coverageInformationString = myAnnotator
      .getPackageCoverageInformationString((PsiPackage)node.getValue(), null, myCoverageDataManager, myStateBean.myFlattenPackages);
    return getNotCoveredMessage(coverageInformationString) + " in package \'" + node.toString() + "\'";
  }

  @Override
  public String getSummaryForRootNode(AbstractTreeNode childNode) {
    final Object value = childNode.getValue();
    String coverageInformationString = myAnnotator.getPackageCoverageInformationString((PsiPackage)value, null,
                                                                                       myCoverageDataManager);
    if (coverageInformationString == null) {
      if (!myCoverageViewManager.isReady()) return "Loading...";
      PackageAnnotator.SummaryCoverageInfo info = new PackageAnnotator.PackageCoverageInfo();
      final Collection children = childNode.getChildren();
      for (Object child : children) {
        final Object childValue = ((CoverageListNode)child).getValue();
        PackageAnnotator.SummaryCoverageInfo childInfo = getSummaryCoverageForNodeValue(childValue);
        info = JavaCoverageAnnotator.merge(info, childInfo);
      }
      coverageInformationString = JavaCoverageAnnotator.getCoverageInformationString(info, false);
    }
    return getNotCoveredMessage(coverageInformationString) + " in 'all classes in scope'";
  }

  private static String getNotCoveredMessage(String coverageInformationString) {
    if (coverageInformationString == null) {
      coverageInformationString = "No coverage";
    }
    return coverageInformationString;
  }

  @Override
  public String getPercentage(int columnIndex, AbstractTreeNode node) {
    final Object value = node.getValue();
    PackageAnnotator.SummaryCoverageInfo info = getSummaryCoverageForNodeValue(value);

    if (columnIndex == 1) {
      return myAnnotator.getClassCoveredPercentage(info);
    } else if (columnIndex == 2){
      return myAnnotator.getMethodCoveredPercentage(info);
    }

    return myAnnotator.getLineCoveredPercentage(info);
  }

  public PackageAnnotator.SummaryCoverageInfo getSummaryCoverageForNodeValue(Object value) {
    if (value instanceof PsiClass) {
      final String qualifiedName = ((PsiClass)value).getQualifiedName();
      return myAnnotator.getClassCoverageInfo(qualifiedName);
    }
    if (value instanceof PsiPackage) {
      return myAnnotator.getPackageCoverageInfo((PsiPackage)value, myStateBean.myFlattenPackages);
    }
    if (value instanceof PsiNamedElement) {
      return myAnnotator.getExtensionCoverageInfo((PsiNamedElement) value);
    }
    return null;
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

  @Override
  public AbstractTreeNode createRootNode() {
    return new CoverageListRootNode(myProject, JavaPsiFacade.getInstance(myProject).findPackage(""), mySuitesBundle, myStateBean);
  }

  @Override
  public List<AbstractTreeNode> createTopLevelNodes() {
    final List<AbstractTreeNode> topLevelNodes = new ArrayList<>();
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

    for (PsiPackage aPackage : packages) {
      final GlobalSearchScope searchScope = mySuitesBundle.getSearchScope(myProject);
      if (aPackage.getClasses(searchScope).length != 0) {
        final CoverageListNode node = new CoverageListNode(myProject, aPackage, mySuitesBundle, myStateBean);
        topLevelNodes.add(node);
      }
      collectSubPackages(topLevelNodes, aPackage);
    }

    for (PsiClass aClass : classes) {
      if (getClassCoverageInfo(aClass) == null) continue;
      topLevelNodes.add(new CoverageListNode(myProject, aClass, mySuitesBundle, myStateBean));
    }
    return topLevelNodes;
  }

  private void collectSubPackages(List<AbstractTreeNode> children, final PsiPackage rootPackage) {
    final GlobalSearchScope searchScope = mySuitesBundle.getSearchScope(rootPackage.getProject());
    final PsiPackage[] subPackages = ApplicationManager.getApplication().runReadAction(new Computable<PsiPackage[]>() {
      public PsiPackage[] compute() {
        return rootPackage.getSubPackages(searchScope); 
      }
    });
    for (final PsiPackage aPackage : subPackages) {
      processSubPackage(aPackage, children);
    }
  }

  private void processSubPackage(final PsiPackage aPackage, List<AbstractTreeNode> children) {
    if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return isInCoverageScope(aPackage);
      }
    })) {
      final CoverageListNode node = new CoverageListNode(aPackage.getProject(), aPackage, mySuitesBundle, myStateBean);
      children.add(node);
    }
    else if (!myStateBean.myFlattenPackages) {
      collectSubPackages(children, aPackage);
    }
    if (myStateBean.myFlattenPackages) {
      collectSubPackages(children, aPackage);
    }
  }

  @Override
  public List<AbstractTreeNode> getChildrenNodes(final AbstractTreeNode node) {
    List<AbstractTreeNode> children = new ArrayList<>();
    if (node instanceof CoverageListNode) {
      final Object val = node.getValue();
      if (val instanceof PsiClass) return Collections.emptyList();

      //append package classes
      if (val instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage) val;
        if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          public Boolean compute() {
            return isInCoverageScope(psiPackage);
          }
        })) {
          final PsiPackage[] subPackages = ApplicationManager.getApplication().runReadAction(new Computable<PsiPackage[]>() {
            public PsiPackage[] compute() {
              return psiPackage.isValid()
                     ? psiPackage.getSubPackages(mySuitesBundle.getSearchScope(node.getProject()))
                     : PsiPackage.EMPTY_ARRAY;
            }
          });
          for (PsiPackage subPackage: subPackages) {
            processSubPackage(subPackage, children);
          }

          final PsiFile[] childFiles = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
            public PsiFile[] compute() {
              return psiPackage.isValid()
                     ? psiPackage.getFiles(mySuitesBundle.getSearchScope(node.getProject()))
                     : PsiFile.EMPTY_ARRAY;
            }
          });
          for (final PsiFile file : childFiles) {
            collectFileChildren(file, node, children);
          }
        }
        else if (!myStateBean.myFlattenPackages) {
          collectSubPackages(children, (PsiPackage)val);
        }
      }
      if (node instanceof CoverageListRootNode) {
        for (CoverageSuite suite : mySuitesBundle.getSuites()) {
          final List<PsiClass> classes = ((JavaCoverageSuite)suite).getCurrentSuiteClasses(myProject);
          for (PsiClass aClass : classes) {
            children.add(new CoverageListNode(myProject, aClass, mySuitesBundle, myStateBean));
          }
        }
      }
      for (AbstractTreeNode childNode : children) {
        childNode.setParent(node);
      }
    }
    return children;
  }

  protected void collectFileChildren(final PsiFile file, AbstractTreeNode node, List<AbstractTreeNode> children) {
    if (file instanceof PsiClassOwner) {
      PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
        public PsiClass[] compute() {
          return file.isValid() ? ((PsiClassOwner) file).getClasses() : PsiClass.EMPTY_ARRAY;
        }
      });
      for (PsiClass aClass : classes) {
        if (!(node instanceof CoverageListRootNode) && getClassCoverageInfo(aClass) == null) continue;
        children.add(new CoverageListNode(myProject, aClass, mySuitesBundle, myStateBean));
      }
    }
  }

  @Nullable
  private PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(final PsiClass aClass) {
    return myAnnotator.getClassCoverageInfo(ApplicationManager.getApplication().runReadAction(new NullableComputable<String>() {
      public String compute() {
        return aClass.isValid() ? aClass.getQualifiedName() : null;
      }
    }));
  }

  @Override
  public ColumnInfo[] createColumnInfos() {
    return new ColumnInfo[]{
      new ElementColumnInfo(), 
      new PercentageCoverageColumnInfo(1, "Class, %", mySuitesBundle, myStateBean), 
      new PercentageCoverageColumnInfo(2, "Method, %", mySuitesBundle, myStateBean),
      new PercentageCoverageColumnInfo(3, "Line, %", mySuitesBundle, myStateBean)
    };
  }

  private boolean isInCoverageScope(PsiElement element) {
    if (element instanceof PsiPackage) {
      final PsiPackage psiPackage = (PsiPackage)element;
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
}
