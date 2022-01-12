// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control.finalVar;

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
    DFAEngine<Boolean> engine = new DFAEngine<>(controlFlow, new MyDfaInstance(VariableDescriptorFactory.createDescriptor(var)), new MySemilattice());
    final List<Boolean> result = engine.performDFAWithTimeout();
    if (result == null) return false;
    Boolean last = result.get(controlFlow.length - 1);
    return last == null ? false : last;
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

  private static class MyDfaInstance implements DfaInstance<Boolean> {
    MyDfaInstance(VariableDescriptor var) {
      myVar = var;
    }

    @Override
    public Boolean fun(@NotNull Boolean e, @NotNull Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction &&
          ((ReadWriteVariableInstruction)instruction).getDescriptor().equals(myVar)) {
        return true;
      }
      return e;
    }

    private final VariableDescriptor myVar;
  }

  private static class MySemilattice implements Semilattice<Boolean> {

    @NotNull
    @Override
    public Boolean join(@NotNull List<? extends Boolean> ins) {
      if (ins.isEmpty()) {
        return false;
      }
      boolean b = true;
      for (boolean candidate : ins) {
        b &= candidate;
      }
      return b;
    }

    @Override
    public boolean eq(@NotNull Boolean e1, @NotNull Boolean e2) {
      return e1.equals(e2);
    }
  }
}
