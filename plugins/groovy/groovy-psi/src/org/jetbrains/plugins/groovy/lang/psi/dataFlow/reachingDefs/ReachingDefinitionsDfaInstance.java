// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private final TObjectIntHashMap<String> myVarToIndexMap = new TObjectIntHashMap<>();
  private final Instruction[] myFlow;

  public int getVarIndex(String varName) {
    return myVarToIndexMap.get(varName);
  }

  public ReachingDefinitionsDfaInstance(Instruction[] flow) {
    myFlow = flow;
    int num = 1;
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction) {
        final String name = ((ReadWriteVariableInstruction) instruction).getVariableName();
        if (!myVarToIndexMap.containsKey(name)) {
          myVarToIndexMap.put(name, num++);
        }
      }
    }
  }


  @Override
  public void fun(@NotNull DefinitionMap m, @NotNull Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction) {
      final ReadWriteVariableInstruction varInsn = (ReadWriteVariableInstruction) instruction;
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
