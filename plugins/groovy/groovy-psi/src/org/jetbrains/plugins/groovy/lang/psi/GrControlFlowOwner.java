// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;

/**
 * Represents elements with control flow cached
 */
public interface GrControlFlowOwner extends GroovyPsiElement {

  Instruction[] getControlFlow();

  @NotNull GroovyControlFlow getGroovyControlFlow();

  boolean isTopControlFlowOwner();
}
