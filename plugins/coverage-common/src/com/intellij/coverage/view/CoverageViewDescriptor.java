package com.intellij.coverage.view;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNamedElement;

public class CoverageViewDescriptor extends NodeDescriptor {
  private final Object myClassOrPackage;

  public CoverageViewDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Object classOrPackage) {
    super(project, parentDescriptor);
    myClassOrPackage = classOrPackage;
    myName = classOrPackage instanceof PsiNamedElement ? ((PsiNamedElement)classOrPackage).getName() : classOrPackage.toString();
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public Object getElement() {
    return myClassOrPackage;
  }
}
