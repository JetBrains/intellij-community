package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import gnu.trove.*;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.ArrayList;

/**
 * @author ven
 */
public class ReachingDefinitionsSemilattice implements Semilattice<TIntObjectHashMap<TIntHashSet>> {
  public TIntObjectHashMap<TIntHashSet> join(ArrayList<TIntObjectHashMap<TIntHashSet>> ins) {
    if (ins.isEmpty()) return new TIntObjectHashMap<TIntHashSet>();

    TIntObjectHashMap<TIntHashSet> result = new TIntObjectHashMap<TIntHashSet>();
    for (TIntObjectHashMap<TIntHashSet> map : ins) {
      merge(result, map);
    }

    return result;
  }

  private void merge(final TIntObjectHashMap<TIntHashSet> result, TIntObjectHashMap<TIntHashSet> map2) {
    map2.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      public boolean execute(int num, TIntHashSet defs) {
        final TIntHashSet defs2 = result.get(num);
        if (defs2 == null) {
          result.put(num, defs);
        } else {
          defs2.addAll(defs.toArray());
        }

        return true;
      }
    });
  }

  public boolean eq(final TIntObjectHashMap<TIntHashSet> m1, final TIntObjectHashMap<TIntHashSet> m2) {
    if (m1.size() != m2.size()) return false;

    return m1.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      public boolean execute(int num, TIntHashSet defs1) {
        final TIntHashSet defs2 = m2.get(num);
        return defs2 != null && defs2.equals(defs1);
      }
    });
  }
}
