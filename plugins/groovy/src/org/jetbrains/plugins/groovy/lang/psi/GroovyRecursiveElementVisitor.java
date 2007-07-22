package org.jetbrains.plugins.groovy.lang.psi;

/**
 * @author ven
 */
public abstract class GroovyRecursiveElementVisitor extends GroovyElementVisitor {

  public void visitElement(GroovyPsiElement element) {
    element.acceptChildren(this);
  }
}
