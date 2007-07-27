/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.util;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

/**
 * @author ilyas
 */
public interface GrVariableDeclarationOwner extends GroovyPsiElement {

  /**
   * Removes variable from its declaration. In case of alone variablein declaration,
   * it also will be removed.
   */
  void removeVariable(GrVariable variable) throws IncorrectOperationException;

}
