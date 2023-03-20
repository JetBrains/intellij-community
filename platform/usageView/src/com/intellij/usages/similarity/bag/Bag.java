// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.bag;

import com.intellij.util.containers.Interner;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

public class Bag {

  public static final Bag EMPTY_BAG = new Bag();
  private static final Interner<String> ourInterner = Interner.createWeakInterner();
  private final @NotNull Object2IntMap<String> myBag;
  private int myCardinality;

  public Bag() {
    myBag = new Object2IntOpenHashMap<>();
    myBag.defaultReturnValue(0);
  }

  public Bag(String @NotNull ... words) {
    myBag = new Object2IntOpenHashMap<>();
    myBag.defaultReturnValue(0);
    for (String word : words) {
      final String internedWord = ourInterner.intern(word);
      myBag.put(internedWord, myBag.getInt(internedWord) + 1);
      myCardinality += 1;
    }
  }

  public @NotNull Object2IntMap<String> getBag() {
    return myBag;
  }

  public void addAll(@NotNull Bag bag) {
    for (Object2IntMap.Entry<String> entry : bag.myBag.object2IntEntrySet()) {
      final String key = entry.getKey();
      myBag.put(key, myBag.getInt(key) + entry.getIntValue());
      myCardinality += entry.getIntValue();
    }
  }

  public int getCardinality() {
    return myCardinality;
  }

  public void add(@NotNull String key) {
    final String internedKey = ourInterner.intern(key);
    myBag.put(internedKey, myBag.getInt(internedKey) + 1);
    myCardinality += 1;
  }

  public int get(@NotNull String word) {
    return myBag.getInt(word);
  }

  public boolean isEmpty() {
    return myBag.isEmpty();
  }

  @Override
  public String toString() {
    StringBuilder toReturn = new StringBuilder();
    for (Object2IntMap.Entry<String> entry : getBag().object2IntEntrySet()) {
      toReturn.append(entry.getKey()).append(" : ").append(entry.getIntValue()).append("\n");
    }
    return toReturn.toString();
  }

  public static int intersectionSize(@NotNull Bag bag1, @NotNull Bag bag2) {
    Bag toIterate = (bag1.getBag().size() > bag2.getBag().size()) ? bag2 : bag1;
    Bag toProcess = (bag1.getBag().size() > bag2.getBag().size()) ? bag1 : bag2;
    int cardinality = 0;
    for (Object2IntMap.Entry<String> entry : toIterate.getBag().object2IntEntrySet()) {
      cardinality += Math.min(toProcess.getBag().getInt(entry.getKey()), toIterate.getBag().getInt(entry.getKey()));
    }
    return cardinality;
  }

  public static @NotNull Bag concat(Bag @NotNull ... bags) {
    Bag bag = new Bag();
    for (Bag it : bags) {
      bag.addAll(it);
    }
    return bag;
  }
}
