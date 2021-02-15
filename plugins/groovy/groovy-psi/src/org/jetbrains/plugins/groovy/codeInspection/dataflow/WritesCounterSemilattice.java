// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;

public final class WritesCounterSemilattice<T> implements Semilattice<Object2IntMap<T>> {
  private static <T> void merge(Object2IntMap<? super T> to, Object2IntMap<T> from) {
    for (Object2IntMap.Entry<T> entry : from.object2IntEntrySet()) {
      T key = entry.getKey();
      to.put(key, to.containsKey(key) ? Math.max(to.getInt(key), entry.getIntValue()) : entry.getIntValue());
    }
  }

  @NotNull
  @Override
  public Object2IntMap<T> initial() {
    return new Object2IntOpenHashMap<>();
  }

  @NotNull
  @Override
  public Object2IntMap<T> join(@NotNull List<? extends Object2IntMap<T>> ins) {
    final Object2IntMap<T> result = new Object2IntOpenHashMap<>();
    for (Object2IntMap<T> i : ins) {
      merge(result, i);
    }
    return result;
  }
}
