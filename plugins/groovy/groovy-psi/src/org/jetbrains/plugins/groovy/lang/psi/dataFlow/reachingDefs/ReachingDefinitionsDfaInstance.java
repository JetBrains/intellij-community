// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;

import java.util.Arrays;

/**
 * @author ven
 */
public class ReachingDefinitionsDfaInstance implements DfaInstance<DefinitionMap> {
  protected final Object2IntMap<VariableDescriptor> myVarToIndexMap;
  private final Instruction[] myFlow;

  public ReachingDefinitionsDfaInstance(Instruction @NotNull [] flow, @NotNull Object2IntMap<VariableDescriptor> varIndexes) {
    myVarToIndexMap = varIndexes;
    myFlow = flow;
  }

  @Override
  public DefinitionMap fun(@NotNull DefinitionMap m, @NotNull Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction) {
      final ReadWriteVariableInstruction varInsn = (ReadWriteVariableInstruction)instruction;
      final VariableDescriptor descriptor = varInsn.getDescriptor();
      assert myVarToIndexMap.containsKey(descriptor) : descriptor + "; " + Arrays.asList(myFlow).contains(instruction);
      final int varIndex = myVarToIndexMap.getInt(descriptor);
      if (varInsn.isWrite()) {
        return m.withRegisteredDef(varIndex, instruction);
      } else {
        return m;
      }
    } else {
      return m;
    }
  }
}
