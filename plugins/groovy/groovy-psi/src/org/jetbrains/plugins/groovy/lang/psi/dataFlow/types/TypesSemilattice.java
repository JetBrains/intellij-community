// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.psi.PsiManager;
import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;
import java.util.Objects;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<TypeDfaState> {

  private final PsiManager myManager;

  public TypesSemilattice(@NotNull PsiManager manager) {
    myManager = manager;
  }
  @NotNull
  @Override
  public TypeDfaState join(@NotNull List<? extends TypeDfaState> ins) {
    if (ins.size() == 0) {
      return TypeDfaState.EMPTY_STATE;
    }
    if (ins.size() == 1) {
      return ins.get(0);
    }

    TypeDfaState result = ins.get(0);

    for (int i = 1; i < ins.size(); i++) {
      if (ins.get(i) != TypeDfaState.EMPTY_STATE) {
        result = TypeDfaState.merge(result, ins.get(i), myManager);
      }
    }
    return result;
  }

  @Override
  public boolean eq(@NotNull TypeDfaState e1, @NotNull TypeDfaState e2) {
    return e1 == e2 || e1.contentsEqual(e2);
  }

  @Contract(pure = true)
  public static Int2ObjectMap<DFAType> mergeForCaching(@NotNull Int2ObjectMap<DFAType> cached,
                                                       @Nullable TypeDfaState candidate) {
    if (candidate == null || candidate.getRawVarTypes().isEmpty()) {
      return cached;
    }

    List<Int2ObjectMap.Entry<DFAType>> newTypes = new SmartList<>();
    for (Int2ObjectMap.Entry<DFAType> candidateEntry : candidate.getRawVarTypes().int2ObjectEntrySet()) {
      int index = candidateEntry.getIntKey();
      if (candidate.isProhibited(index) || (cached.containsKey(index) && checkDfaStatesConsistency(cached, candidateEntry))) {
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

  private static boolean checkDfaStatesConsistency(@NotNull Int2ObjectMap<DFAType> cached,
                                                   @NotNull Int2ObjectMap.Entry<DFAType> incoming) {
    if (!ApplicationManager.getApplication().isUnitTestMode() ||
        ApplicationManagerEx.isInStressTest() ||
        DfaCacheConsistencyKt.mustSkipConsistencyCheck()) {
      return true;
    }
    DFAType cachedType = cached.get(incoming.getIntKey());
    if (cachedType != null && !Objects.equals(cachedType, incoming.getValue())) {
      throw new IllegalStateException("Attempt to cache different types: for descriptor " +
                                      incoming.getIntKey() +
                                      ", existing was " +
                                      cachedType +
                                      " and incoming is " +
                                      incoming.getValue());
    }
    return true;
  }
}
