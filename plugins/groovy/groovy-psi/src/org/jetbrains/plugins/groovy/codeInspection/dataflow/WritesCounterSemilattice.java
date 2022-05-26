// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;

public final class WritesCounterSemilattice implements Semilattice<Object2IntMap<GrVariable>> {

  private static final Object2IntMap<GrVariable> NEUTRAL = new Object2IntOpenHashMap<>();

  private static <T> void merge(Object2IntMap<? super T> to, Object2IntMap<T> from) {
    for (Object2IntMap.Entry<T> entry : from.object2IntEntrySet()) {
      T key = entry.getKey();
      to.put(key, to.containsKey(key) ? Math.max(to.getInt(key), entry.getIntValue()) : entry.getIntValue());
    }
  }


  @NotNull
  @Override
  public Object2IntMap<GrVariable> join(@NotNull List<? extends Object2IntMap<GrVariable>> ins) {
    if (ins.isEmpty()) {
      return NEUTRAL;
    }
    if (ins.size() == 1) {
      return ins.get(0);
    }
    final Object2IntMap<GrVariable> result = new Object2IntOpenHashMap<>();
    for (Object2IntMap<GrVariable> i : ins) {
      merge(result, i);
    }
    return result;
  }
}
