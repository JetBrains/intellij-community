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
    final String coverageInformationString = myAnnotator
      .getPackageCoverageInformationString((PsiPackage)node.getValue(), null, myCoverageDataManager, myStateBean.myFlattenPackages);
    return "Coverage Summary for Package \'" + node.toString() + "\': " + getNotCoveredMessage(coverageInformationString);
  }

  @Override
  public String getSummaryForRootNode(AbstractTreeNode childNode) {
    final Object value = childNode.getValue();
    String coverageInformationString = myAnnotator.getPackageCoverageInformationString((PsiPackage)value, null,
                                                                                       myCoverageDataManager);
    if (coverageInformationString == null) {
      if (!myCoverageViewManager.isReady()) return "Loading...";
      PackageAnnotator.PackageCoverageInfo info = new PackageAnnotator.PackageCoverageInfo();
      final Collection children = childNode.getChildren();
      for (Object child : children) {
        final Object childValue = ((CoverageListNode)child).getValue();
        if (childValue instanceof PsiPackage) {
          final PackageAnnotator.PackageCoverageInfo coverageInfo = myAnnotator.getPackageCoverageInfo((PsiPackage)childValue, myStateBean.myFlattenPackages);
          if (coverageInfo != null) {
            info = JavaCoverageAnnotator.merge(info, coverageInfo);
          }
        } else {
          final PackageAnnotator.ClassCoverageInfo classCoverageInfo = getClassCoverageInfo(((PsiClass)childValue));
          if (classCoverageInfo != null) {
            info.coveredClassCount += classCoverageInfo.coveredMethodCount > 0 ? 1 : 0;
            info.totalClassCount ++;

            info.coveredMethodCount += classCoverageInfo.coveredMethodCount;
            info.totalMethodCount += classCoverageInfo.totalMethodCount;

            info.coveredLineCount += classCoverageInfo.partiallyCoveredLineCount + classCoverageInfo.fullyCoveredLineCount;
            info.totalLineCount += classCoverageInfo.totalLineCount;
          }
        }
      }
      coverageInformationString = JavaCoverageAnnotator.getCoverageInformationString(info, false);
    }
    return "Coverage Summary for \'all classes in scope\': " + getNotCoveredMessage(coverageInformationString);
  }

  private static String getNotCoveredMessage(String coverageInformationString) {
    if (coverageInformationString == null) {
      coverageInformationString = "not covered";
    }
    return coverageInformationString;
  }

  @Override
  public String getPercentage(int columnIndex, AbstractTreeNode node) {
    final Object value = node.getValue();
    if (value instanceof PsiClass) {

      //no coverage gathered
      if (((PsiClass)value).isInterface()) return null;

      final String qualifiedName = ((PsiClass)value).getQualifiedName();
      if (columnIndex == 1) {
        return myAnnotator.getClassCoveredPercentage(qualifiedName);
      } else if (columnIndex == 2){
        return myAnnotator.getClassMethodPercentage(qualifiedName);
      }
      
      return myAnnotator.getClassLinePercentage(qualifiedName);
    }
    if (value instanceof PsiPackage) {
      final boolean flatten = myStateBean.myFlattenPackages;
      if (columnIndex == 1) {
        return myAnnotator.getPackageClassPercentage((PsiPackage)value, flatten);
      } else if (columnIndex == 2) {
        return myAnnotator.getPackageMethodPercentage((PsiPackage)value, flatten);
      }
      return myAnnotator.getPackageLinePercentage((PsiPackage)value, flatten);
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
    final List<AbstractTreeNode> topLevelNodes = new ArrayList<AbstractTreeNode>();
    final LinkedHashSet<PsiPackage> packages = new LinkedHashSet<PsiPackage>();
    final LinkedHashSet<PsiClass> classes = new LinkedHashSet<PsiClass>();
    for (CoverageSuite suite : mySuitesBundle.getSuites()) {
      packages.addAll(((JavaCoverageSuite)suite).getCurrentSuitePackages(myProject));
      classes.addAll(((JavaCoverageSuite)suite).getCurrentSuiteClasses(myProject));
    }

    final Set<PsiPackage> packs = new HashSet<PsiPackage>();
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
      if (aPackage.getDirectories(searchScope).length == 0) continue;
      if (aPackage.getClasses(searchScope).length != 0) {
        final CoverageListNode node = new CoverageListNode(myProject, aPackage, mySuitesBundle, myStateBean);
        topLevelNodes.add(node);
      }
      collectSubPackages(topLevelNodes, aPackage, mySuitesBundle, myStateBean);
    }

    for (PsiClass aClass : classes) {
      if (getClassCoverageInfo(aClass) == null) continue;
      topLevelNodes.add(new CoverageListNode(myProject, aClass, mySuitesBundle, myStateBean));
    }
    return topLevelNodes;
  }

  private static void collectSubPackages(List<AbstractTreeNode> children,
                                         final PsiPackage rootPackage,
                                         final CoverageSuitesBundle data,
                                         final CoverageViewManager.StateBean stateBean) {
    final GlobalSearchScope searchScope = data.getSearchScope(rootPackage.getProject());
    final PsiPackage[] subPackages = ApplicationManager.getApplication().runReadAction(new Computable<PsiPackage[]>() {
      public PsiPackage[] compute() {
        return rootPackage.getSubPackages(searchScope); 
      }
    });
    for (final PsiPackage aPackage : subPackages) {
      final PsiDirectory[] directories = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory[]>() {
        public PsiDirectory[] compute() {
          return aPackage.getDirectories(searchScope); 
        }
      });
      if (directories.length == 0 && !ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return JavaPsiFacade.getInstance(aPackage.getProject()).isPartOfPackagePrefix(aPackage.getQualifiedName());
        }
      })) continue;
      if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return isInCoverageScope(aPackage, data);
        }
      })) {
        final CoverageListNode node = new CoverageListNode(rootPackage.getProject(), aPackage, data, stateBean);
        children.add(node);
      }
      else if (!stateBean.myFlattenPackages) {
        collectSubPackages(children, aPackage, data, stateBean);
      }
      if (stateBean.myFlattenPackages) {
        collectSubPackages(children, aPackage, data, stateBean);
      }
    }
  }


  @Override
  public List<AbstractTreeNode> getChildrenNodes(final AbstractTreeNode node) {
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    if (node instanceof CoverageListNode) {
      final Object val = node.getValue();
      if (val instanceof PsiClass) return Collections.emptyList();

      //append package classes
      if (val instanceof PsiPackage) {
        if (!myStateBean.myFlattenPackages) {
          collectSubPackages(children, (PsiPackage)val, mySuitesBundle, myStateBean);
        }
        if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          public Boolean compute() {
            return isInCoverageScope((PsiPackage)val, mySuitesBundle);
          }
        })) {
          final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
            public PsiClass[] compute() {
              return ((PsiPackage)val).getClasses(mySuitesBundle.getSearchScope(node.getProject()));
            }
          });
          for (PsiClass aClass : classes) {
            if (!(node instanceof CoverageListRootNode) && getClassCoverageInfo(aClass) == null) continue;
            children.add(new CoverageListNode(myProject, aClass, mySuitesBundle, myStateBean));
          }
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

  @Nullable
  private PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(final PsiClass aClass) {
    return myAnnotator.getClassCoverageInfo(ApplicationManager.getApplication().runReadAction(new NullableComputable<String>() {
      public String compute() {
        return aClass.getQualifiedName();
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

  private static boolean isInCoverageScope(PsiElement element, CoverageSuitesBundle suitesBundle) {
    if (element instanceof PsiPackage) {
      final PsiPackage psiPackage = (PsiPackage)element;
      final String qualifiedName = psiPackage.getQualifiedName();
      for (CoverageSuite suite : suitesBundle.getSuites()) {
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
      return isInCoverageScope(JavaPsiFacade.getInstance(myProject).findPackage(packageName), mySuitesBundle);
    }
    if (object instanceof PsiPackage) {
      return isInCoverageScope((PsiElement)object, mySuitesBundle);
    }
    return false;
  }

  @Override
  public boolean supportFlattenPackages() {
    return true;
  }
}
