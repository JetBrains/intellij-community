package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2007
 */
public class GroovyPsiElementVisitor extends PsiElementVisitor {
  GroovyElementVisitor myGroovyElementVisitor;

  public GroovyPsiElementVisitor(GroovyElementVisitor groovyElementVisitor) {
    myGroovyElementVisitor = groovyElementVisitor;
  }

  public void visitElement(PsiElement element) {
    if (element instanceof GroovyPsiElement) {
      ((GroovyPsiElement) element).accept(myGroovyElementVisitor);
    }
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
  }
}
