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
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 1/2/12
 */
public class CoverageViewTreeStructure extends AbstractTreeStructure {
  private final PsiPackage myRootPackage;
  private final CoverageSuitesBundle myData;
  private final CoverageViewManager.StateBean myStateBean;
  private final CoverageListNode myRootNode;
  private final Map<String,List<String>> myPackages;
  private final List<AbstractTreeNode> myTopLevelPackages;

  public CoverageViewTreeStructure(Project project, CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean) {
    myData = bundle;
    myStateBean = stateBean;
    myRootPackage = JavaPsiFacade.getInstance(project).findPackage("");

    myPackages = new HashMap<String, List<String>>();
    buildPackageIndex(bundle.getCoverageData(), myPackages);
    myTopLevelPackages = new ArrayList<AbstractTreeNode>();
    myRootNode = new CoverageListNode(myRootPackage, myData, myPackages, myTopLevelPackages, myStateBean);

    for (CoverageSuite suite : bundle.getSuites()) {
      final List<PsiPackage> packages = ((JavaCoverageSuite)suite).getCurrentSuitePackages(project);
      for (PsiPackage aPackage : packages) {
        final CoverageListNode node = new CoverageListNode(aPackage, myData, myPackages, myTopLevelPackages, myStateBean);
        myTopLevelPackages.add(node);
        collectSubPackages(myTopLevelPackages, aPackage, myPackages);
      }
      final List<PsiClass> classes = ((JavaCoverageSuite)suite).getCurrentSuiteClasses(project);
      for (PsiClass aClass : classes) {
        myTopLevelPackages.add(new CoverageListNode(aClass, bundle, myPackages, myTopLevelPackages, myStateBean));
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
    return getChildren(element, myData, myPackages, myTopLevelPackages, myStateBean);
  }

  public boolean contains(String packageName) {
    return myPackages.containsKey(packageName);
  }

  static Object[] getChildren(Object element,
                              final CoverageSuitesBundle bundle,
                              Map<String, List<String>> packages,
                              List<AbstractTreeNode> topLevelPackages,
                              CoverageViewManager.StateBean stateBean) {
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    if (element instanceof CoverageListNode) {
      Object val = ((CoverageListNode)element).getValue();
      if (val instanceof PsiClass) return ArrayUtil.EMPTY_OBJECT_ARRAY;

      final ProjectData data = bundle.getCoverageData();
      //append package classes
      if (val instanceof PsiPackage) {
        if (stateBean.myFlattenPackages) {
          if (StringUtil.isEmpty(((PsiPackage)val).getQualifiedName())) {
            for (AbstractTreeNode topLevelPackageNode : topLevelPackages) {
              children.add(topLevelPackageNode);
            }
          }
        } else {
          final PsiPackage[] subPackages = ((PsiPackage)val).getSubPackages();
          for (PsiPackage aPackage : subPackages) {
            final PsiDirectory[] directories = aPackage.getDirectories(GlobalSearchScope.projectScope(aPackage.getProject()));
            if (directories.length == 0) continue;
            if (packages.containsKey(aPackage.getQualifiedName())) {
              children.add(new CoverageListNode(aPackage, bundle, packages, topLevelPackages, stateBean));
            }
          }
        }


        final PsiClass[] classes = ((PsiPackage)val).getClasses();
        for (PsiClass aClass : classes) {
          final ClassData classData = data.getClassData(aClass.getQualifiedName());
          if (classData != null) {
            final CoverageListNode treeNode = new CoverageListNode(aClass, bundle, packages, topLevelPackages, stateBean);
            children.add(treeNode);
          }
        }
      }
      for (AbstractTreeNode node : children) {
        node.setParent((AbstractTreeNode)element);
      }
    }
    return children.toArray(new CoverageListNode[children.size()]);
  }


  private static void buildPackageIndex(ProjectData data, Map<String, List<String>> packages) {
    for (Object o : data.getClasses().keySet()) {
      registerClassName(packages, (String)o);
    }
  }

  private static void registerClassName(Map<String, List<String>> packages, String className) {
    final String packageName = StringUtil.getPackageName(className);
    List<String> classNames = packages.get(packageName);
    if (classNames == null) {
      classNames = new ArrayList<String>();
      packages.put(packageName, classNames);
      registerClassName(packages, packageName);
    }
    classNames.add(className);
  }

  private void collectSubPackages(List<AbstractTreeNode> children, PsiPackage rootPackage, Map<String, List<String>> packages) {
    final PsiPackage[] subPackages = rootPackage.getSubPackages();
    for (PsiPackage aPackage : subPackages) {
      if (packages.containsKey(aPackage.getQualifiedName())) {
        final CoverageListNode node = new CoverageListNode(aPackage, myData, packages, myTopLevelPackages, myStateBean);
        children.add(node);
        collectSubPackages(children, aPackage, packages);
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

