// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * Represents elements with control flow cached
 *
 * @author ven
 */
public interface GrControlFlowOwner extends GroovyPsiElement {

  Instruction[] getControlFlow();

  boolean isTopControlFlowOwner();
}
