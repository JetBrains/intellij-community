// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class DefinitionMap {
  public static final DefinitionMap NEUTRAL = new DefinitionMap(new Int2ObjectOpenHashMap<>());

  private final @NotNull Int2ObjectMap<IntSet> myMap;

  private DefinitionMap(@NotNull Int2ObjectMap<IntSet> map) {
    this.myMap = map;
  }

  @Contract(pure = true)
  @NotNull
  public DefinitionMap withRegisteredDef(int varIndex, @NotNull Instruction instruction) {
    if (varIndex == 0) {
      return this;
    }
    Int2ObjectMap<IntSet> newMap = new Int2ObjectOpenHashMap<>(myMap);
    IntSet defs = new IntArraySet(1);
    newMap.put(varIndex, defs);
    defs.add(instruction.num());
    return new DefinitionMap(newMap);
  }

  @Contract(pure = true)
  @NotNull
  public DefinitionMap withMerged(DefinitionMap other) {
    if (other == this || other == NEUTRAL) {
      return this;
    }
    if (this == NEUTRAL) {
      return other;
    }
    Int2ObjectMap<IntSet> newMap = new Int2ObjectOpenHashMap<>(myMap);
    List<Integer> toRemove = new SmartList<>();
    for (int key : myMap.keySet()) {
      if (!other.myMap.containsKey(key)) {
        toRemove.add(key);
      }
    }
    for (int key : toRemove) {
      newMap.remove(key);
    }
    for (Int2ObjectMap.Entry<IntSet> entry : other.myMap.int2ObjectEntrySet()) {
      int varIndex = entry.getIntKey();
      IntSet otherVarDefs = entry.getValue();
      IntSet myDefs = newMap.get(varIndex);
      if (myDefs != null) {
        IntSet newDefs = new IntArraySet(myDefs);
        newDefs.addAll(otherVarDefs);
        newMap.put(varIndex, newDefs);
      }
    }
    return new DefinitionMap(newMap);
  }

  public @Nullable IntSet getDefinitions(int varIndex) {
    return myMap.get(varIndex);
  }

  public void forEachValue(Consumer<IntSet> procedure) {
    myMap.values().forEach(procedure);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DefinitionMap map = (DefinitionMap)o;
    return Objects.equals(myMap, map.myMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMap);
  }

  @Override
  public String toString() {
    return myMap.toString();
  }
}
