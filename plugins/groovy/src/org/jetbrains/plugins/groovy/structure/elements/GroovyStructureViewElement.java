package org.jetbrains.plugins.groovy.structure.elements;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */

abstract public class GroovyStructureViewElement implements StructureViewTreeElement {
  final protected PsiElement myElement;

  public GroovyStructureViewElement(PsiElement element) {
    myElement = element;
  }

  public Object getValue() {
    return myElement.isValid() ? myElement : null;
  }

  public void navigate(boolean b) {
    ((Navigatable) myElement).navigate(b);
  }

  public boolean canNavigate() {
    return ((Navigatable) myElement).canNavigate();
  }

  public boolean canNavigateToSource() {
    return ((Navigatable) myElement).canNavigateToSource();
  }
}
