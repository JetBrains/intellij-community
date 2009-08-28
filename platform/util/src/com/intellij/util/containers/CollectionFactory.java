/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import gnu.trove.THashMap;
import gnu.trove.THashSet;

import java.util.*;

/**
 * @author peter
 */
public class CollectionFactory {
  private CollectionFactory() {
  }

  public static <T> Set<T> newTroveSet(T... elements) {
    return newTroveSet(Arrays.asList(elements));
  }

  public static <T> Set<T> newTroveSet(Collection<T> elements) {
    return new THashSet<T>(elements);
  }

  public static <T> T[] ar(T... elements) {
    return elements;
  }

  public static <T,V> THashMap<T, V> newTroveMap() {
    return new THashMap<T,V>();
  }

  public static <T> ArrayList<T> arrayList() {
    return new ArrayList<T>();
  }

  public static <T, V> LinkedHashMap<T, V> linkedMap() {
    return new LinkedHashMap<T,V>();
  }
}
