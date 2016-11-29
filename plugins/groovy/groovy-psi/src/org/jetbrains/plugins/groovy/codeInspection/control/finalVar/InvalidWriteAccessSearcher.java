/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.control.finalVar;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class InvalidWriteAccessSearcher {
  @Nullable
  public static List<ReadWriteVariableInstruction> findInvalidWriteAccess(@NotNull Instruction[] flow,
                                                                          @NotNull Map<String, GrVariable> variables,
                                                                          @NotNull Set<GrVariable> alreadyInitialized) {
    DFAEngine<MyData> engine = new DFAEngine<>(flow, new MyDFAInstance(), new MySemilattice());
    final ArrayList<MyData> dfaResult = engine.performDFAWithTimeout();
    if (dfaResult == null) return null;


    List<ReadWriteVariableInstruction> result = ContainerUtil.newArrayList();
    for (int i = 0; i < flow.length; i++) {
      Instruction instruction = flow[i];
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction)instruction).isWrite()) {
        final MyData initialized = dfaResult.get(i);
        final GrVariable var = variables.get(((ReadWriteVariableInstruction)instruction).getVariableName());
        if (alreadyInitialized.contains(var)) {
          if (initialized.isInitialized(((ReadWriteVariableInstruction)instruction).getVariableName())) {
            result.add((ReadWriteVariableInstruction)instruction);
          }
        }
        else {
          if (initialized.isOverInitialized(((ReadWriteVariableInstruction)instruction).getVariableName())) {
            result.add((ReadWriteVariableInstruction)instruction);
          }
        }
      }
    }


    return result;
  }

  private static class MyDFAInstance implements DfaInstance<MyData> {
    @Override
    public void fun(MyData e, Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction)instruction).isWrite()) {
        e.add(((ReadWriteVariableInstruction)instruction).getVariableName());
      }
    }

    @NotNull
    @Override
    public MyData initial() {
      return new MyData();
    }

    @Override
    public boolean isForward() {
      return true;
    }
  }

  private static class MySemilattice implements Semilattice<MyData> {
    @NotNull
    @Override
    public MyData join(@NotNull ArrayList<MyData> ins) {
      return new MyData(ins);
    }

    @Override
    public boolean eq(MyData e1, MyData e2) {
      return e1.equals(e2);
    }
  }

  private static class MyData {
    private final Set<String> myInitialized = ContainerUtil.newHashSet();
    private final Set<String> myOverInitialized = ContainerUtil.newHashSet();

    public MyData(List<MyData> ins) {
      for (MyData data : ins) {
        myInitialized.addAll(data.myInitialized);
        myOverInitialized.addAll(data.myOverInitialized);
      }
    }

    public MyData() {
    }

    public void add(String var) {
      if (!myInitialized.add(var)) {
        myOverInitialized.add(var);
      }
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyData &&
             myInitialized.equals(((MyData)obj).myInitialized) &&
             myOverInitialized.equals(((MyData)obj).myOverInitialized);
    }

    public boolean isOverInitialized(String var) {
      return myOverInitialized.contains(var);
    }

    public boolean isInitialized(String var) {
      return myInitialized.contains(var);
    }
  }
}
