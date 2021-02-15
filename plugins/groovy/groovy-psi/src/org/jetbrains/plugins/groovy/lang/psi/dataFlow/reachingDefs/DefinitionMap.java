// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.function.Consumer;

/**
 * @author peter
 */
public final class DefinitionMap {
  private final Int2ObjectMap<IntSet> myMap = new Int2ObjectOpenHashMap<>();

  public void registerDef(Instruction varInsn, int varId) {
    IntSet defs = myMap.get(varId);
    if (defs == null) {
      myMap.put(varId, defs = new IntOpenHashSet());
    }
    else {
      defs.clear();
    }
    defs.add(varInsn.num());
  }

  public void merge(DefinitionMap map2) {
    for (Int2ObjectMap.Entry<IntSet> entry : map2.myMap.int2ObjectEntrySet()) {
      IntSet defs2 = myMap.get(entry.getIntKey());
      if (defs2 == null) {
        defs2 = new IntOpenHashSet(entry.getValue());
        myMap.put(entry.getIntKey(), defs2);
      }
      else {
        defs2.addAll(entry.getValue());
      }
    }
  }

  public boolean eq(DefinitionMap m2) {
    if (myMap.size() != m2.myMap.size()) {
      return false;
    }

    for (Int2ObjectMap.Entry<IntSet> entry : myMap.int2ObjectEntrySet()) {
      IntSet defs2 = m2.myMap.get(entry.getIntKey());
      if (defs2 == null || !defs2.equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  public void copyFrom(DefinitionMap map, int fromIndex, int toIndex) {
    IntSet defs = map.myMap.get(fromIndex);
    if (defs == null) {
      defs = new IntOpenHashSet();
    }
    myMap.put(toIndex, defs);
  }

  public int @Nullable [] getDefinitions(int varId) {
    IntSet set = myMap.get(varId);
    return set == null ? null : set.toIntArray();
  }

  public void forEachValue(Consumer<IntSet> procedure) {
    myMap.values().forEach(procedure);
  }
}
