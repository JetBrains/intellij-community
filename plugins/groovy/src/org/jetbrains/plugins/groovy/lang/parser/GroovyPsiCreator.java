package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.extapi.psi.ASTWrapperPsiElement;

/**
 * Creates Groovy PSI element by given AST node
 *
 * @author Ilya.Sergey
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
