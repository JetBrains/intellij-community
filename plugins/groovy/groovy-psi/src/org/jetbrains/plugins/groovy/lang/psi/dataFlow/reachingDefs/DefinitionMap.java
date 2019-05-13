/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

/**
 * @author peter
 */
public class DefinitionMap {
  private final TIntObjectHashMap<TIntHashSet> myMap = new TIntObjectHashMap<>();

  public void registerDef(Instruction varInsn, int varId) {
    TIntHashSet defs = myMap.get(varId);
    if (defs == null) {
      myMap.put(varId, defs = new TIntHashSet());
    } else {
      defs.clear();
    }
    defs.add(varInsn.num());
  }

  public void merge(DefinitionMap map2) {
    map2.myMap.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      @Override
      public boolean execute(int num, TIntHashSet defs) {
        TIntHashSet defs2 = myMap.get(num);
        if (defs2 == null) {
          defs2 = new TIntHashSet(defs.toArray());
          myMap.put(num, defs2);
        }
        else {
          defs2.addAll(defs.toArray());
        }

        return true;
      }
    });
  }

  public boolean eq(final DefinitionMap m2) {
    if (myMap.size() != m2.myMap.size()) return false;

    return myMap.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      @Override
      public boolean execute(int num, TIntHashSet defs1) {
        final TIntHashSet defs2 = m2.myMap.get(num);
        return defs2 != null && defs2.equals(defs1);
      }
    });
  }

  public void copyFrom(DefinitionMap map, int fromIndex, int toIndex) {
    TIntHashSet defs = map.myMap.get(fromIndex);
    if (defs == null) defs = new TIntHashSet();
    myMap.put(toIndex, defs);
  }

  @Nullable
  public int[] getDefinitions(int varId) {
    TIntHashSet set = myMap.get(varId);
    return set == null ? null : set.toArray();
  }

  public void forEachValue(TObjectProcedure<TIntHashSet> procedure) {
    myMap.forEachValue(procedure);
  }
}
