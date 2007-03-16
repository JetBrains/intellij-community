package org.jetbrains.plugins.groovy.lang.psi.impl;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.annotations.NotNull;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;

/**
 * @author Ilya.Sergey
 */
public class GroovyPsiElementImpl extends ASTWrapperPsiElement implements GroovyPsiElement {

  public GroovyPsiElementImpl(@NotNull ASTNode node) {
    super(node);
  }

}
