package com.intellij.coverage.view;

import com.intellij.coverage.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: 1/5/12
 */
public class JavaCoverageViewExtension extends CoverageViewExtension {
  private JavaCoverageAnnotator myAnnotator;

  public JavaCoverageViewExtension(JavaCoverageAnnotator annotator) {
    myAnnotator = annotator;
  }

  @Override
  public String getSummaryForNode(AbstractTreeNode node) {
    return "Coverage Summary for Package \'" + node.toString() + "\': " +
           myAnnotator.getPackageCoverageInformationString((PsiPackage)node.getValue(), null, CoverageDataManager.getInstance(node.getProject()));
  }

  @Override
  public String getSummaryForRootNode(AbstractTreeNode childNode) {
    final Object value = childNode.getValue();
    String coverageInformationString = myAnnotator.getPackageCoverageInformationString((PsiPackage)value, null, CoverageDataManager.getInstance(childNode.getProject()));
    if (coverageInformationString == null) {
      PackageAnnotator.PackageCoverageInfo info = new PackageAnnotator.PackageCoverageInfo();
      final Collection children = childNode.getChildren();
      for (Object child : children) {
        final Object childValue = ((CoverageListNode)child).getValue();
        if (childValue instanceof PsiPackage) {
          final PackageAnnotator.PackageCoverageInfo coverageInfo = myAnnotator.getPackageCoverageInfo((PsiPackage)childValue);
          if (coverageInfo != null) {
            info = JavaCoverageAnnotator.merge(info, coverageInfo);
          }
        }
      }
      coverageInformationString = JavaCoverageAnnotator.getCoverageInformationString(info, false);
    }
    return "Coverage Summary for \'all classes in scope\': " + coverageInformationString;
  }

  @Override
  public String getPercentage(int columnIndex, AbstractTreeNode node, boolean flatten) {
    final Object value = node.getValue();
    if (value instanceof PsiClass) {
      final String qualifiedName = ((PsiClass)value).getQualifiedName();
      if (columnIndex == 1) {
        return myAnnotator.isClassCovered(qualifiedName) ? "100% (1/1)" : "0% (0/1)";
      } else if (columnIndex == 2){
        return myAnnotator.getClassMethodPercentage(qualifiedName);
      }
      
      return myAnnotator.getClassLinePercentage(qualifiedName);
    }
    if (columnIndex == 1) {
      return myAnnotator.getPackageClassPercentage((PsiPackage)value, flatten);
    } else if (columnIndex == 2) {
      return myAnnotator.getPackageMethodPercentage((PsiPackage)value, flatten);
    }
    return myAnnotator.getPackageLinePercentage((PsiPackage)value, flatten);
  }

  @Override
  public PsiElement getElementToSelect(VirtualFile virtualFile, Project project) {
    final PsiElement psiElement = super.getElementToSelect(virtualFile, project);
    if (psiElement instanceof PsiClassOwner) {
      final PsiClass[] classes = ((PsiClassOwner)psiElement).getClasses();
      if (classes.length > 0) return classes[0];
    }
    return psiElement;
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
  public AbstractTreeNode createRootNode(CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean, Project project) {
    return new CoverageListRootNode(JavaPsiFacade.getInstance(project).findPackage(""), bundle, stateBean);
  }

  @Override
  public List<AbstractTreeNode> createTopLevelNodes(CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean, Project project) {
    final List<AbstractTreeNode> topLevelNodes = new ArrayList<AbstractTreeNode>();
    for (CoverageSuite suite : bundle.getSuites()) {
      final List<PsiPackage> packages = ((JavaCoverageSuite)suite).getCurrentSuitePackages(project);
      for (PsiPackage aPackage : packages) {
        if (aPackage.getDirectories(getSearchScope(bundle, project)).length == 0) continue;
        final CoverageListNode node = new CoverageListNode(aPackage, bundle, stateBean);
        topLevelNodes.add(node);
        collectSubPackages(topLevelNodes, aPackage, bundle, stateBean);
      }
      final List<PsiClass> classes = ((JavaCoverageSuite)suite).getCurrentSuiteClasses(project);
      for (PsiClass aClass : classes) {
        topLevelNodes.add(new CoverageListNode(aClass, bundle, stateBean));
      }
    }
    return topLevelNodes;
  }

  private static GlobalSearchScope getSearchScope(CoverageSuitesBundle bundle, Project project) {
    return bundle.isTrackTestFolders() ? GlobalSearchScope.projectScope(project) : GlobalSearchScopes.projectProductionScope(project);
  }
  
  private static void collectSubPackages(List<AbstractTreeNode> children,
                                         final PsiPackage rootPackage,
                                         final CoverageSuitesBundle data,
                                         final CoverageViewManager.StateBean stateBean) {
    final GlobalSearchScope searchScope = getSearchScope(data, rootPackage.getProject());
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
      if (directories.length == 0) continue;
      if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return isInCoverageScope(aPackage, data);
        }
      })) {
        final CoverageListNode node = new CoverageListNode(aPackage, data, stateBean);
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
  public List<AbstractTreeNode> getChildrenNodes(final AbstractTreeNode node,
                                                 final CoverageSuitesBundle suitesBundle,
                                                 final CoverageViewManager.StateBean stateBean) {
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    if (node instanceof CoverageListNode) {
      final Object val = node.getValue();
      if (val instanceof PsiClass) return Collections.emptyList();

      //append package classes
      if (val instanceof PsiPackage) {
        boolean found = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          public Boolean compute() {
            return isInCoverageScope((PsiPackage)val, suitesBundle); 
          }
        });
        if (!stateBean.myFlattenPackages) {
          collectSubPackages(children, (PsiPackage)val, suitesBundle, stateBean);
        }

        final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
          public PsiClass[] compute() {
            return ((PsiPackage)val).getClasses(getSearchScope(suitesBundle, node.getProject()));
          }
        });
        if (found) {
          for (PsiClass aClass : classes) {
            children.add(new CoverageListNode(aClass, suitesBundle, stateBean));
          }
        }
        else {                                                                        
          for (final PsiClass aClass : classes) {
            final String classFQName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
              public String compute() {
                return aClass.getQualifiedName(); 
              }
            });
            for (CoverageSuite suite : suitesBundle.getSuites()) {
              if (((JavaCoverageSuite)suite).isClassFiltered(classFQName)) {
                children.add(new CoverageListNode(aClass, suitesBundle, stateBean));
              }
            }
          }
        }
      }
      for (AbstractTreeNode childNode : children) {
        childNode.setParent(node);
      }
    }
    return children;
  }

  public static boolean isInCoverageScope(PsiElement element, CoverageSuitesBundle suitesBundle) {
    final PsiPackage psiPackage = (PsiPackage)element;
    final String qualifiedName = psiPackage.getQualifiedName();
    Set<String> filteredClasses = new HashSet<String>();
    for (CoverageSuite suite : suitesBundle.getSuites()) {
      if (((JavaCoverageSuite)suite).isPackageFiltered(qualifiedName)) return true;
      Collections.addAll(filteredClasses, ((JavaCoverageSuite)suite).getFilteredClassNames());
    }
    if (!filteredClasses.isEmpty()) {
      final PsiClass[] classes = psiPackage.getClasses(getSearchScope(suitesBundle, psiPackage.getProject()));
      for (PsiClass psiClass : classes) {
        final String classFQName = psiClass.getQualifiedName();
        for (CoverageSuite suite : suitesBundle.getSuites()) {
          if (((JavaCoverageSuite)suite).isClassFiltered(classFQName)) return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean canSelectInCoverageView(VirtualFile file, Project project, CoverageSuitesBundle bundle) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile instanceof PsiClassOwner) {
      final String packageName = ((PsiClassOwner)psiFile).getPackageName();
      return isInCoverageScope(JavaPsiFacade.getInstance(project).findPackage(packageName), bundle);
    }
    return false;
  }
}
