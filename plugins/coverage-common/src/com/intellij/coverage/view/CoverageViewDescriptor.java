package com.intellij.coverage.view;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNamedElement;

public class CoverageViewDescriptor extends NodeDescriptor {
  private final Object myElement;

  public CoverageViewDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Object element) {
    super(project, parentDescriptor);
    myElement = element;
    myName = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : element.toString();
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public Object getElement() {
    return myElement;
  }
}
