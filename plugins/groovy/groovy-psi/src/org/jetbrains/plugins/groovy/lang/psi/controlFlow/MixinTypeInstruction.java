// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;

/**
 * @author Max Medvedev
 */
public interface MixinTypeInstruction extends Instruction {
  @Nullable
  ReadWriteVariableInstruction getInstructionToMixin(Instruction[] flow);

  @Nullable
  PsiType inferMixinType();

  /**
   * @return 0 if not found
   */
  int getVariableDescriptor();

  @Nullable
  ConditionInstruction getConditionInstruction();
}
