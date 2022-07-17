// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * Represents elements with control flow cached
 *
 * @author ven
 */
public interface GrControlFlowOwner extends GroovyPsiElement {

  /**
   * @see org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils#getGroovyControlFlow(GrControlFlowOwner)
   */
  Instruction[] getControlFlow();

  boolean isTopControlFlowOwner();
}
