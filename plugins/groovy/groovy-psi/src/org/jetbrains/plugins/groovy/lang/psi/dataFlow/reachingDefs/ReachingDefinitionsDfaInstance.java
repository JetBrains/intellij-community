// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;

public class ReachingDefinitionsDfaInstance implements DfaInstance<DefinitionMap> {

  public static final Logger LOG = Logger.getInstance(ReachingDefinitionsDfaInstance.class);

  @Override
  public DefinitionMap fun(@NotNull DefinitionMap m, @NotNull Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction varInsn) {
      final int descriptor = varInsn.getDescriptor();
      if (varInsn.isWrite()) {
        return m.withRegisteredDef(descriptor, instruction);
      } else {
        return m;
      }
    } else {
      return m;
    }
  }
}
