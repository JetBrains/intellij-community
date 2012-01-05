package com.intellij.coverage.view;

import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.JavaCoverageSuite;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/2/12
 */
public class CoverageViewTreeStructure extends AbstractTreeStructure {
  private final PsiPackage myRootPackage;
  private final CoverageSuitesBundle myData;
  private final CoverageViewManager.StateBean myStateBean;
  private final CoverageListNode myRootNode;
  private final List<AbstractTreeNode> myTopLevelPackages;
  private final List<String> myFilteredPackages;
  private final List<String> myFilteredClasses;

  public CoverageViewTreeStructure(Project project, CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean) {
    myData = bundle;
    myStateBean = stateBean;
    myRootPackage = JavaPsiFacade.getInstance(project).findPackage("");

    myTopLevelPackages = new ArrayList<AbstractTreeNode>();
    myFilteredPackages = new ArrayList<String>();
    myFilteredClasses = new ArrayList<String>();

    myRootNode = new CoverageListNode(myRootPackage, myData, myTopLevelPackages, myStateBean, myFilteredPackages,
                                      myFilteredClasses);

    for (CoverageSuite suite : bundle.getSuites()) {
      final List<PsiPackage> packages = ((JavaCoverageSuite)suite).getCurrentSuitePackages(project);
      for (PsiPackage aPackage : packages) {
        myFilteredPackages.add(aPackage.getQualifiedName());
        final CoverageListNode node = new CoverageListNode(aPackage, myData, myTopLevelPackages, myStateBean,
                                                           myFilteredPackages, myFilteredClasses);
        myTopLevelPackages.add(node);
        collectSubPackages(myTopLevelPackages, aPackage, myData, myTopLevelPackages, myStateBean, myFilteredPackages, myFilteredClasses, true);
      }
      final List<PsiClass> classes = ((JavaCoverageSuite)suite).getCurrentSuiteClasses(project);
      for (PsiClass aClass : classes) {
        myFilteredClasses.add(aClass.getQualifiedName());
        myTopLevelPackages.add(new CoverageListNode(aClass, bundle, myTopLevelPackages, myStateBean, myFilteredPackages,
                                                    myFilteredClasses));
      }
    }
    for (AbstractTreeNode abstractTreeNode : myTopLevelPackages) {
      abstractTreeNode.setParent(myRootNode);
    }
  }


  public Object getRootElement() {
    return myRootNode;
  }

  public Object[] getChildElements(final Object element) {
    return getChildren(element, myData, myFilteredPackages, myFilteredClasses, myTopLevelPackages, myStateBean);
  }

  public boolean contains(String packageName) {
    return contains(packageName, myFilteredPackages);
  }

  public static boolean contains(String packageName, final List<String> packages) {
    if (packages.contains(packageName)) return true;
    for (String aPackage : packages) {
      if (packageName.startsWith(aPackage + ".")) return true;
    }
    //todo check classes
    return false;
  }

  static Object[] getChildren(Object element,
                              final CoverageSuitesBundle bundle,
                              List<String> filteredPackages, List<String> filteredClasses, List<AbstractTreeNode> topLevelPackages,
                              CoverageViewManager.StateBean stateBean) {
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    if (element instanceof CoverageListNode) {
      Object val = ((CoverageListNode)element).getValue();
      if (val instanceof PsiClass) return ArrayUtil.EMPTY_OBJECT_ARRAY;

      //append package classes
      if (val instanceof PsiPackage) {
        final String qualifiedName = ((PsiPackage)val).getQualifiedName();
        boolean found = contains(qualifiedName, filteredPackages);
        if (stateBean.myFlattenPackages) {
          if (StringUtil.isEmpty(qualifiedName)) {
            for (AbstractTreeNode topLevelPackageNode : topLevelPackages) {
              children.add(topLevelPackageNode);
            }
          }
        } else {
          collectSubPackages(children, (PsiPackage)val, bundle, topLevelPackages, stateBean, filteredPackages, filteredClasses, false);
        }

        if (found) {
          final PsiClass[] classes = ((PsiPackage)val).getClasses();
          for (PsiClass aClass : classes) {
            children.add(new CoverageListNode(aClass, bundle, topLevelPackages, stateBean, filteredPackages, filteredClasses));
          }
        }
        if (!found && !filteredClasses.isEmpty()) {
          final PsiClass[] classes = ((PsiPackage)val).getClasses();
          for (PsiClass aClass : classes) {
            if (filteredClasses.contains(aClass.getQualifiedName())) {
              children.add(new CoverageListNode(aClass, bundle, topLevelPackages, stateBean, filteredPackages, filteredClasses));
            }
          }
        }
      }
      for (AbstractTreeNode node : children) {
        node.setParent((AbstractTreeNode)element);
      }
    }
    return children.toArray(new CoverageListNode[children.size()]);
  }

  private static void collectSubPackages(List<AbstractTreeNode> children,
                                         PsiPackage rootPackage,
                                         final CoverageSuitesBundle data,
                                         final List<AbstractTreeNode> packages,
                                         final CoverageViewManager.StateBean stateBean,
                                         final List<String> filteredPackages,
                                         final List<String> filteredClasses,
                                         final boolean flattenPackages) {
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(rootPackage.getProject());
    final PsiPackage[] subPackages = rootPackage.getSubPackages();
    for (PsiPackage aPackage : subPackages) {
      final PsiDirectory[] directories = aPackage.getDirectories(searchScope);
      if (directories.length == 0) continue;
      if (contains(aPackage.getQualifiedName(), filteredPackages)) {
        final CoverageListNode node = new CoverageListNode(aPackage, data, packages, stateBean, filteredPackages,
                                                           filteredClasses);
        children.add(node);
      } else if (!flattenPackages) {
        collectSubPackages(children, aPackage, data, packages, stateBean, filteredPackages, filteredClasses, flattenPackages);
      }
      if (flattenPackages) {
        collectSubPackages(children, aPackage, data, packages, stateBean, filteredPackages, filteredClasses, flattenPackages);
      }
    }
  }

  public Object getParentElement(final Object element) {
    if (element instanceof PsiClass) {
      final PsiDirectory containingDirectory = ((PsiClass)element).getContainingFile().getContainingDirectory();
      return containingDirectory != null ? JavaDirectoryService.getInstance().getPackage(containingDirectory) : null;
    }
    return ((PsiPackage)element).getParentPackage();
  }

  @NotNull
  public CoverageViewDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    return new CoverageViewDescriptor(myRootPackage.getProject(), parentDescriptor, element);
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }
}

