/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.ArrayList;

public class WritesCounterSemilattice<T> implements Semilattice<TObjectIntHashMap<T>> {

  private static <T> void merge(final TObjectIntHashMap<T> to, TObjectIntHashMap<T> from) {
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
  public TObjectIntHashMap<T> join(@NotNull ArrayList<TObjectIntHashMap<T>> ins) {
    final TObjectIntHashMap<T> result = new TObjectIntHashMap<>();
    for (TObjectIntHashMap<T> i : ins) {
      merge(result, i);
    }
    return result;
  }

  @Override
  public boolean eq(TObjectIntHashMap<T> e1, TObjectIntHashMap<T> e2) {
    return e1.equals(e2);
  }
}
