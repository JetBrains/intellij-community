// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.psi.PsiManager;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<TypeDfaState> {
  private final PsiManager myManager;
  private final Map<VariableDescriptor, Integer> varIndexes;

  private final TypeDfaState initialState;

  public TypesSemilattice(@NotNull PsiManager manager, @NotNull TypeDfaState initialState, Map<VariableDescriptor, Integer> varIndexes) {
    myManager = manager;
    this.initialState = initialState;
    this.varIndexes = varIndexes;
  }

  @Override
  @NotNull
  public TypeDfaState initial() {
    return new TypeDfaState(initialState);
  }

  @NotNull
  @Override
  public TypeDfaState join(@NotNull List<? extends TypeDfaState> ins) {
    if (ins.isEmpty()) return initial();

    TypeDfaState result = new TypeDfaState(ins.get(0));
    if (ins.size() == 1) {
      return result;
    }

    for (int i = 1; i < ins.size(); i++) {
      result.joinState(ins.get(i), myManager, varIndexes);
    }
    return result;
  }

  @Override
  public boolean eq(@NotNull TypeDfaState e1, @NotNull TypeDfaState e2) {
    return e1.contentsEqual(e2);
  }

  public static Map<VariableDescriptor, DFAType> mergeForCaching(Map<VariableDescriptor, DFAType> cached,
                                                                 TypeDfaState another,
                                                                 Map<VariableDescriptor, Integer> varIndexes) {
    if (another.getVarTypes().isEmpty()) {
      return cached;
    }

    List<Map.Entry<VariableDescriptor, DFAType>> newTypes = new SmartList<>();
    for (Map.Entry<VariableDescriptor, DFAType> candidateEntry : another.getVarTypes().entrySet()) {
      var descriptor = candidateEntry.getKey();
      if (another.getProhibitedCachingVars().get(varIndexes.getOrDefault(descriptor, 0)) ||
          (cached.containsKey(descriptor) && checkDfaStatesConsistency(cached, candidateEntry))) {
        continue;
      }
      newTypes.add(candidateEntry);
    }
    if (newTypes.isEmpty()) {
      return cached;
    }
    Map<VariableDescriptor, DFAType> newState = new HashMap<>(cached.size() + newTypes.size());
    newState.putAll(cached);
    for (var entry : newTypes) {
      newState.put(entry.getKey(), entry.getValue());
    }
    return newState;
  }

  private static boolean checkDfaStatesConsistency(@NotNull Map<VariableDescriptor, DFAType> cached,
                                                   @NotNull Map.Entry<VariableDescriptor, DFAType> incoming) {
    if (!ApplicationManager.getApplication().isUnitTestMode() ||
        ApplicationManagerEx.isInStressTest() ||
        DfaCacheConsistencyKt.mustSkipConsistencyCheck()) {
      return true;
    }
    DFAType cachedType = cached.get(incoming.getKey());
    if (cachedType != null && !Objects.equals(cachedType, incoming.getValue())) {
      throw new IllegalStateException("Attempt to cache different types: for descriptor " +
                                      incoming.getKey() +
                                      ", existing was " +
                                      cachedType +
                                      " and incoming is " +
                                      incoming.getValue());
    }
    return true;
  }
}
