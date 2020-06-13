// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.control.finalVar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.VariableDescriptorFactory;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Max Medvedev
 */
public final class InvalidWriteAccessSearcher {
  @Nullable
  public static List<ReadWriteVariableInstruction> findInvalidWriteAccess(Instruction @NotNull [] flow,
                                                                          @NotNull Set<? extends GrVariable> variables,
                                                                          @NotNull Set<? extends GrVariable> alreadyInitialized) {
    DFAEngine<MyData> engine = new DFAEngine<>(flow, new MyDFAInstance(), new MySemilattice());
    final List<MyData> dfaResult = engine.performDFAWithTimeout();
    if (dfaResult == null) return null;


    List<ReadWriteVariableInstruction> result = new ArrayList<>();

    Set<VariableDescriptor> descriptors = variables.stream()
      .map(VariableDescriptorFactory::createDescriptor)
      .collect(Collectors.toSet());

    Set<VariableDescriptor> initializedDescriptors = alreadyInitialized.stream()
      .map(VariableDescriptorFactory::createDescriptor)
      .collect(Collectors.toSet());

    for (int i = 0; i < flow.length; i++) {
      Instruction instruction = flow[i];
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction)instruction).isWrite()) {
        final MyData initialized = dfaResult.get(i);
        VariableDescriptor descriptor = ((ReadWriteVariableInstruction)instruction).getDescriptor();
        if (!descriptors.contains(descriptor)) continue;
        if (initializedDescriptors.contains(descriptor)) {
          if (initialized.isInitialized(descriptor)) {
            result.add((ReadWriteVariableInstruction)instruction);
          }
        }
        else {
          if (initialized.isOverInitialized(descriptor)) {
            result.add((ReadWriteVariableInstruction)instruction);
          }
        }
      }
    }


    return result;
  }

  private static class MyDFAInstance implements DfaInstance<MyData> {
    @Override
    public void fun(@NotNull MyData e, @NotNull Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction)instruction).isWrite()) {
        e.add(((ReadWriteVariableInstruction)instruction).getDescriptor());
      }
    }
  }

  private static class MySemilattice implements Semilattice<MyData> {

    @NotNull
    @Override
    public MyData initial() {
      return new MyData();
    }

    @NotNull
    @Override
    public MyData join(@NotNull List<? extends MyData> ins) {
      return new MyData(ins);
    }
  }

  private static class MyData {
    private final Set<VariableDescriptor> myInitialized = new HashSet<>();
    private final Set<VariableDescriptor> myOverInitialized = new HashSet<>();

    MyData(List<? extends MyData> ins) {
      for (MyData data : ins) {
        myInitialized.addAll(data.myInitialized);
        myOverInitialized.addAll(data.myOverInitialized);
      }
    }

    MyData() {
    }

    public void add(VariableDescriptor var) {
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

    public boolean isOverInitialized(VariableDescriptor var) {
      return myOverInitialized.contains(var);
    }

    public boolean isInitialized(VariableDescriptor var) {
      return myInitialized.contains(var);
    }
  }
}
