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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GroovyStructureViewElement that = (GroovyStructureViewElement)o;

    if (myElement != null ? !myElement.equals(that.myElement) : that.myElement != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElement != null ? myElement.hashCode() : 0;
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
