package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class GrInterfaceDefImpl extends GroovyPsiElementImpl implements GrInterfaceDef {
  public GrInterfaceDefImpl(@NotNull ASTNode node) {
    super(node);
  }
}