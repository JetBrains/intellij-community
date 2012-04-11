package com.intellij.coverage.view;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNamedElement;

/**
 * User: anna
 * Date: 1/2/12
 */
public class CoverageViewDescriptor extends NodeDescriptor {
  private final Object myClassOrPackage;
 
  public CoverageViewDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Object classOrPackage) {
    super(project, parentDescriptor);
    myClassOrPackage = classOrPackage;
    myName = classOrPackage instanceof PsiNamedElement ? ((PsiNamedElement)classOrPackage).getName() : classOrPackage.toString();
  }

  public boolean update() {
    return false;
  }

  public Object getElement() {
    return myClassOrPackage;
  }
}
  