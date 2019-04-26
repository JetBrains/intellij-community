// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;

public class WritesCounterSemilattice<T> implements Semilattice<TObjectIntHashMap<T>> {

  private static <T> void merge(final TObjectIntHashMap<? super T> to, TObjectIntHashMap<T> from) {
    from.forEachEntry(new TObjectIntProcedure<T>() {
      @Override
      public boolean execute(T key, int value) {
        to.put(
          key,
          to.containsKey(key) ? Math.max(to.get(key), value) : value
        );
        return true;
      }
    });
  }

  @NotNull
  @Override
  public TObjectIntHashMap<T> initial() {
    return new TObjectIntHashMap<>();
  }

  @NotNull
  @Override
  public TObjectIntHashMap<T> join(@NotNull List<? extends TObjectIntHashMap<T>> ins) {
    final TObjectIntHashMap<T> result = new TObjectIntHashMap<>();
    for (TObjectIntHashMap<T> i : ins) {
      merge(result, i);
    }
    return result;
  }
}
