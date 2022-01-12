// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control.finalVar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.VariableDescriptorFactory;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.*;
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
    final List<@Nullable MyData> dfaResult = engine.performDFAWithTimeout();
    if (dfaResult == null) return null;


    List<ReadWriteVariableInstruction> result = new ArrayList<>();

    Set<VariableDescriptor> descriptors = variables.stream()
      .filter(Objects::nonNull)
      .map(VariableDescriptorFactory::createDescriptor)
      .collect(Collectors.toSet());

    Set<VariableDescriptor> initializedDescriptors = alreadyInitialized.stream()
      .filter(Objects::nonNull)
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
    public MyData fun(@NotNull MyData e, @NotNull Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction)instruction).isWrite()) {
        return e.add(((ReadWriteVariableInstruction)instruction).getDescriptor());
      } else {
        return e;
      }
    }
  }

  private static class MySemilattice implements Semilattice<MyData> {
    private static final MyData NEUTRAL = new MyData();
    @NotNull
    @Override
    public MyData join(@NotNull List<? extends MyData> ins) {
      return new MyData(ins);
    }
  }

  private static class MyData {
    private final @Unmodifiable Set<VariableDescriptor> myInitialized;
    private final @Unmodifiable Set<VariableDescriptor> myOverInitialized;

    @SuppressWarnings("ConstantConditions")
    MyData(List<? extends MyData> ins) {
      this(new HashSet<>(), new HashSet<>());
      for (MyData data : ins) {
        myInitialized.addAll(data.myInitialized);
        myOverInitialized.addAll(data.myOverInitialized);
      }
    }

    MyData() {
      this(Set.of(), Set.of());
    }

    MyData(Set<VariableDescriptor> initialized, Set<VariableDescriptor> overInitialized) {
      myInitialized = initialized;
      myOverInitialized = overInitialized;
    }

    public MyData add(VariableDescriptor var) {
      if (myInitialized.contains(var)) {
        Set<VariableDescriptor> newOverInitialized;
        if (myOverInitialized.contains(var)) {
          newOverInitialized = myOverInitialized;
        } else {
          newOverInitialized = new HashSet<>(myOverInitialized);
          newOverInitialized.add(var);
        }
        return new MyData(myInitialized, newOverInitialized);
      } else {
        HashSet<VariableDescriptor> newInitialized = new HashSet<>(myInitialized);
        newInitialized.add(var);
        return new MyData(newInitialized, myOverInitialized);
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
