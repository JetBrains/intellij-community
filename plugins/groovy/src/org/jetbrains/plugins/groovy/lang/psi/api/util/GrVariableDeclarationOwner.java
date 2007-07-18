/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.util;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ilyas
 */
public interface GrVariableDeclarationOwner {

  /**
   * Removes variable from its declaration. In case of alone variablein declaration,
   * it also will be removed.
   * @param variable variable vto be removed
   * @throws IncorrectOperationException
   */
  void removeVariable(GrVariable variable) throws IncorrectOperationException;

}
