/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.util;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * @author ilyas
 */
public interface GrVariableDeclarationOwner extends GroovyPsiElement, GrStatementOwner {

  /**
   * Removes variable from its declaration. In case of alone variablein declaration,
   * it also will be removed.
   */
  void removeVariable(GrVariable variable) throws IncorrectOperationException;

  /**
   * Adds new variable declaration after anchor spectified. If anchor == null, adds variable at owner's first position
   * @param anchor Anchor after which new variabler declaration will be placed
   * @return
   * @throws IncorrectOperationException
   */
  GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException;

}
