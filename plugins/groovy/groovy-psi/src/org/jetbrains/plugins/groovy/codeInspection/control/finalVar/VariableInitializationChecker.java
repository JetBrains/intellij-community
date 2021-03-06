// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.control.finalVar;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.VariableDescriptorFactory;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public final class VariableInitializationChecker {

  private static boolean isVariableDefinitelyInitialized(@NotNull GrVariable var, Instruction @NotNull [] controlFlow) {
    DFAEngine<Data> engine = new DFAEngine<>(controlFlow, new MyDfaInstance(VariableDescriptorFactory.createDescriptor(var)), new MySemilattice());
    final List<Data> result = engine.performDFAWithTimeout();
    if (result == null) return false;

    return result.get(controlFlow.length - 1).get();
  }

  public static boolean isVariableDefinitelyInitializedCached(@NotNull GrVariable var,
                                                              @NotNull GroovyPsiElement context,
                                                              Instruction @NotNull [] controlFlow) {
    Map<GroovyPsiElement, Boolean> map = CachedValuesManager.getCachedValue(var, () -> Result.create(new HashMap<>(), var));

    final Boolean cached = map.get(context);
    if (cached != null) return cached.booleanValue();

    final boolean result = isVariableDefinitelyInitialized(var, controlFlow);
    map.put(context, result);

    return result;
  }

  private static class MyDfaInstance implements DfaInstance<Data> {
    MyDfaInstance(VariableDescriptor var) {
      myVar = var;
    }

    @Override
    public void fun(@NotNull Data e, @NotNull Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction &&
          ((ReadWriteVariableInstruction)instruction).getDescriptor().equals(myVar)) {
        e.set(true);
      }
    }

    private final VariableDescriptor myVar;
  }

  private static class MySemilattice implements Semilattice<Data> {

    @NotNull
    @Override
    public Data initial() {
      return new Data(false);
    }

    @NotNull
    @Override
    public Data join(@NotNull List<? extends Data> ins) {
      if (ins.isEmpty()) return new Data(false);

      boolean b = true;
      for (Data data : ins) {
        b &= data.get().booleanValue();
      }

      return new Data(b);
    }

    @Override
    public boolean eq(@NotNull Data e1, @NotNull Data e2) {
      return e1.get().booleanValue() == e2.get().booleanValue();
    }
  }

  private static class Data extends Ref<Boolean> {
    Data(Boolean value) {
      super(value);
    }
  }
}
