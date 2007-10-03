package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author ven
 */
public interface GrCall extends GroovyPsiElement {
  GroovyPsiElement getArgumentList();
}
