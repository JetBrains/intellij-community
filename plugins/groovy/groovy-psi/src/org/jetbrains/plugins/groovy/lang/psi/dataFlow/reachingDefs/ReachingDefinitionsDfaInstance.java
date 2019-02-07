// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;

import java.util.Arrays;

/**
 * @author ven
 */
public class ReachingDefinitionsDfaInstance implements DfaInstance<DefinitionMap> {

  protected final TObjectIntHashMap<String> myVarToIndexMap;
  private final Instruction[] myFlow;

  public ReachingDefinitionsDfaInstance(@NotNull Instruction[] flow, @NotNull TObjectIntHashMap<String> varIndexes) {
    myVarToIndexMap = varIndexes;
    myFlow = flow;
  }

  @Override
  public void fun(@NotNull DefinitionMap m, @NotNull Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction) {
      final ReadWriteVariableInstruction varInsn = (ReadWriteVariableInstruction)instruction;
      final String name = varInsn.getVariableName();
      assert myVarToIndexMap.containsKey(name) : name + "; " + Arrays.asList(myFlow).contains(instruction);
      final int num = myVarToIndexMap.get(name);
      if (varInsn.isWrite()) {
        m.registerDef(varInsn, num);
      }
    }
  }

  @Override
  @NotNull
  public DefinitionMap initial() {
    return new DefinitionMap();
  }
}
