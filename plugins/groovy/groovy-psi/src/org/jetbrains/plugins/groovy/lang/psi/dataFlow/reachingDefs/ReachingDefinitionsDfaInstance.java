/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import gnu.trove.*;
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
    int num = 0;
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
  public void fun(DefinitionMap m, Instruction instruction) {
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

  @Override
  public boolean isForward() {
    return true;
  }
}
