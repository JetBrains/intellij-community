package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

/**
 * Creates Groovy PSI element by given AST node
 *
 * @author Ilya.Sergey, Dmitry.Krasilschikov
 */
public abstract class GroovyPsiCreator {

  /**
   * Creates Groovy PSI element by given AST node
   *
   * @param node Given node
   * @return Respective PSI element
   */
  public static PsiElement createElement(ASTNode node) {
    IElementType elem = node.getElementType();

    return new ASTWrapperPsiElement(node);
  }

}
