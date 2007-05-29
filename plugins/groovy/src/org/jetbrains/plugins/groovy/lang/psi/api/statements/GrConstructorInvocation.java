package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.05.2007
 */
public interface GrConstructorInvocation extends GrStatement {
  GrArgumentList getArgumentList();

  boolean isSuperCall();

  boolean isThisCall();
}
