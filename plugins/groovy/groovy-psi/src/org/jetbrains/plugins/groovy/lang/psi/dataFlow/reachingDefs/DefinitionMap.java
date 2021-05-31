// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author peter
 */
public final class DefinitionMap {
  private final Int2ObjectMap<IntSet> myMap = new Int2ObjectOpenHashMap<>();

  public void registerDef(int varIndex, Instruction instruction) {
    IntSet defs = myMap.get(varIndex);
    if (defs == null) {
      myMap.put(varIndex, defs = new IntOpenHashSet());
    }
    else {
      defs.clear();
    }
    defs.add(instruction.num());
  }

  public void mergeFrom(DefinitionMap other) {
    for (Int2ObjectMap.Entry<IntSet> entry : other.myMap.int2ObjectEntrySet()) {
      int varIndex = entry.getIntKey();
      IntSet otherDefs = entry.getValue();
      IntSet myDefs = myMap.get(varIndex);
      if (myDefs == null) {
        myDefs = new IntOpenHashSet(otherDefs);
        myMap.put(varIndex, myDefs);
      }
      else {
        myDefs.addAll(otherDefs);
      }
    }
  }

  public int @Nullable [] getDefinitions(int varIndex) {
    IntSet defs = myMap.get(varIndex);
    return defs == null ? null : defs.toIntArray();
  }

  public void forEachValue(Consumer<IntSet> procedure) {
    myMap.values().forEach(procedure);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DefinitionMap map = (DefinitionMap)o;
    return myMap.equals(map.myMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMap);
  }
}
