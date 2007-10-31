package org.jetbrains.plugins.groovy.structure.elements.impl;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.structure.elements.GroovyStructureViewElement;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.impl.GroovyMethodItemPresentation;

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.10.2007
 */
public class GroovyMethodStructureViewElement extends GroovyStructureViewElement {
  private final boolean isInherit;

  public GroovyMethodStructureViewElement(PsiMethod element, boolean isInherit) {
    super(element);
    this.isInherit = isInherit;
  }

  public ItemPresentation getPresentation() {
    return new GroovyMethodItemPresentation(((PsiMethod) myElement), isInherit);
  }

  public TreeElement[] getChildren() {
    return StructureViewTreeElement.EMPTY_ARRAY;
  }
}
