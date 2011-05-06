/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author ven
 */
public class ReachingDefinitionsDfaInstance implements DfaInstance<TIntObjectHashMap<TIntHashSet>> {
  private final TObjectIntHashMap<String> myVarToIndexMap = new TObjectIntHashMap<String>();

  public int getVarIndex(String varName) {
    return myVarToIndexMap.get(varName);
  }

  public ReachingDefinitionsDfaInstance(Instruction[] flow) {
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


  public void fun(TIntObjectHashMap<TIntHashSet> m, Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction) {
      final ReadWriteVariableInstruction varInsn = (ReadWriteVariableInstruction) instruction;
      final String name = varInsn.getVariableName();
      assert myVarToIndexMap.containsKey(name);
      final int num = myVarToIndexMap.get(name);
      if (varInsn.isWrite()) {
        registerDef(m, varInsn, num);
      }
    }
  }

  protected static void registerDef(TIntObjectHashMap<TIntHashSet> m, Instruction varInsn, int num) {
    TIntHashSet defs = m.get(num);
    if (defs == null) {
      defs = new TIntHashSet();
      m.put(num, defs);
    } else defs.clear();
    defs.add(varInsn.num());
  }

  @NotNull
  public TIntObjectHashMap<TIntHashSet> initial() {
    return new TIntObjectHashMap<TIntHashSet>();
  }

  public boolean isForward() {
    return true;
  }
}
