// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.psi.PsiManager;
import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<TypeDfaState> {
  private final PsiManager myManager;
  private final Map<VariableDescriptor, Integer> varIndexes;

  static final TypeDfaState NEUTRAL = new TypeDfaState();

  public TypesSemilattice(@NotNull PsiManager manager,
                          Map<VariableDescriptor, Integer> varIndexes) {
    myManager = manager;
    this.varIndexes = varIndexes;
  }

  @Override
  @NotNull
  public TypeDfaState initial() {
    return NEUTRAL;
  }

  @NotNull
  @Override
  public TypeDfaState join(@NotNull List<? extends TypeDfaState> ins) {
    TypeDfaState result = ins.get(0);

    for (int i = 1; i < ins.size(); i++) {
      result = result.withMerged(ins.get(i), myManager, varIndexes);
    }
    return result;
  }

  @Override
  public boolean eq(@NotNull TypeDfaState e1, @NotNull TypeDfaState e2) {
    return e1 == e2 || e1.contentsEqual(e2);
  }

  public static Int2ObjectMap<DFAType> mergeForCaching(Int2ObjectMap<DFAType> cached,
                                                       TypeDfaState another) {
    if (another.getRawVarTypes().isEmpty()) {
      return cached;
    }

    List<Int2ObjectMap.Entry<DFAType>> newTypes = new SmartList<>();
    for (Int2ObjectMap.Entry<DFAType> candidateEntry : another.getRawVarTypes().int2ObjectEntrySet()) {
      int index = candidateEntry.getIntKey();
      if (index == 0 || another.getProhibitedCachingVars().get(index) ||
          (cached.containsKey(index) /*&& checkDfaStatesConsistency(cached, candidateEntry)*/)) {
        continue;
      }
      newTypes.add(candidateEntry);
    }
    if (newTypes.isEmpty()) {
      return cached;
    }
    Int2ObjectMap<DFAType> newState = new Int2ObjectOpenHashMap<>(cached.size() + newTypes.size());
    newState.putAll(cached);
    for (var entry : newTypes) {
      newState.put(entry.getIntKey(), entry.getValue());
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
