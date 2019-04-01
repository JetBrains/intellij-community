// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  VariableDescriptor getVariableDescriptor();

  @Nullable
  ConditionInstruction getConditionInstruction();
}
