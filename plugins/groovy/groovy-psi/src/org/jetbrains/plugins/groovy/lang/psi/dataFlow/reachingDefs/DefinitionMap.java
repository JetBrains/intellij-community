// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import com.intellij.util.containers.FList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author peter
 */
public final class DefinitionMap {
  private final Int2ObjectMap<IntSet> myMap = new Int2ObjectOpenHashMap<>();
  private FList<DefinitionMap> myPreviousClosureContext = FList.emptyList();

  public void registerDef(int varIndex, Instruction instruction) {
    IntSet defs = myMap.get(varIndex);
    if (defs == null) {
      myMap.put(varIndex, defs = new IntArraySet(1));
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
        myDefs = new IntArraySet(otherDefs);
        myMap.put(varIndex, myDefs);
      }
      else {
        myDefs.addAll(otherDefs);
      }
    }
    if (!other.myPreviousClosureContext.isEmpty()) {
      // not a hack actually; we may want to merge with instructions further in the flow, like with for-statements
      myPreviousClosureContext = other.myPreviousClosureContext;
    }
  }

  public void mergeDefs(DefinitionMap other) {
    for (Int2ObjectMap.Entry<IntSet> entry : other.myMap.int2ObjectEntrySet()) {
      int varIndex = entry.getIntKey();
      IntSet otherDefs = entry.getValue();
      IntSet myDefs = myMap.get(varIndex);
      if (myDefs == null) {
        myDefs = new IntArraySet(otherDefs);
        myMap.put(varIndex, myDefs);
      }
      else {
        myDefs.addAll(otherDefs);
      }
    }
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
    return Objects.equals(myMap, map.myMap) && Objects.equals(myPreviousClosureContext, map.myPreviousClosureContext);
  }

  public void setClosureContext(@NotNull DefinitionMap map) {
    myPreviousClosureContext = myPreviousClosureContext.prepend(map);
  }

  public void popClosureContext() {
    myPreviousClosureContext = myPreviousClosureContext.getTail();
  }

  public @NotNull DefinitionMap getHeadDefinitionMap() {
    var head = myPreviousClosureContext.getHead();
    assert head != null;
    return head;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMap, System.identityHashCode(myPreviousClosureContext));
  }
}
