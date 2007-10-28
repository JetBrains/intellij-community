package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author ven
 */
public interface GrCall extends GroovyPsiElement {
  @Nullable
  GroovyPsiElement getArgumentList();
}
