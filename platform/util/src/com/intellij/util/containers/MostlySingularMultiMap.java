// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;

@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "myMap.entrySet().toArray()")
public class MostlySingularMultiMap<K, V> implements Serializable {
  private static final long serialVersionUID = 2784473565881807109L;

  protected final Map<K, Object> myMap; // K -> V|ValueList<V>

  public MostlySingularMultiMap() {
    myMap = CollectionFactory.createSmallMemoryFootprintMap();
  }

  public MostlySingularMultiMap(@NotNull Map<K, Object> map) {
    myMap = map;
  }

  public void add(@NotNull K key, @NotNull V value) {
    Object current = myMap.get(key);
    if (current == null) {
      myMap.put(key, value);
    }
    else if (current instanceof ValueList) {
      //noinspection unchecked
      ((List<V>)current).add(value);
    }
    else {
      List<V> newList = new ValueList<>();
      //noinspection unchecked
      newList.add((V)current);
      newList.add(value);
      myMap.put(key, newList);
    }
  }

  public boolean remove(@NotNull K key, @NotNull V value) {
    Object current = myMap.get(key);
    if (current == null) {
      return false;
    }
    else if (current instanceof ValueList) {
      //noinspection unchecked
      return ((List<V>)current).remove(value);
    }
    else if (value.equals(current)) {
      myMap.remove(key);
      return true;
    }
    return false;
  }

  public boolean removeAllValues(@NotNull K key) {
    return myMap.remove(key) != null;
  }

  public final @NotNull Set<K> keySet() {
    return myMap.keySet();
  }

  public final boolean isEmpty() {
    return myMap.isEmpty();
  }

  public final boolean processForKey(@NotNull K key, @NotNull Processor<? super V> p) {
    return processValue(p, myMap.get(key));
  }

  private boolean processValue(@NotNull Processor<? super V> p, Object v) {
    if (v instanceof ValueList) {
      //noinspection unchecked
      for (V o : (ValueList<V>)v) {
        if (!p.process(o)) {
          return false;
        }
      }
      return true;
    }
    else {
      //noinspection unchecked
      return v == null || p.process((V)v);
    }
  }

  public boolean processAllValues(@NotNull Processor<? super V> p) {
    for (Object v : myMap.values()) {
      if (!processValue(p, v)) {
        return false;
      }
    }
    return true;
  }

  public final int size() {
    return myMap.size();
  }

  public final boolean containsKey(@NotNull K key) {
    return myMap.containsKey(key);
  }

  public final int valuesForKey(@NotNull K key) {
    Object current = myMap.get(key);
    if (current == null) {
      return 0;
    }
    else if (current instanceof ValueList) {
      //noinspection unchecked
      return ((ValueList<V>)current).size();
    }
    return 1;
  }

  public final @NotNull Iterable<V> get(@NotNull K name) {
    return rawValueToCollection(myMap.get(name));
  }

  protected final @NotNull List<V> rawValueToCollection(Object value) {
    if (value == null) {
      return Collections.emptyList();
    }
    else if (value instanceof ValueList) {
      //noinspection unchecked
      return (ValueList<V>)value;
    }
    else {
      //noinspection unchecked
      return Collections.singletonList((V)value);
    }
  }

  public void compact() {
    CollectionFactory.trimMap(myMap);
    for (Object eachValue : myMap.values()) {
      if (eachValue instanceof ValueList) {
        //noinspection unchecked
        ((ValueList<V>)eachValue).trimToSize();
      }
    }
  }

  @Override
  public final String toString() {
    return "{" + StringUtil.join(myMap.entrySet(), entry -> {
      Object value = entry.getValue();
      String s = (value instanceof ValueList ? value : Collections.singletonList(value)).toString();
      return entry.getKey() + ": " + s;
    }, "; ") + "}";
  }

  public void clear() {
    myMap.clear();
  }

  @NotNull
  public static <K, V> MostlySingularMultiMap<K, V> emptyMap() {
    //noinspection unchecked
    return (MostlySingularMultiMap<K, V>)EmptyMap.EMPTY;
  }

  public void addAll(@NotNull MostlySingularMultiMap<K, V> other) {
    if (other.isEmpty()) {
      return;
    }

    for (Map.Entry<? extends K, Object> entry : other.myMap.entrySet()) {
      K key = entry.getKey();
      Object otherValue = entry.getValue();
      Object myValue = myMap.get(key);

      if (myValue == null) {
        if (otherValue instanceof ValueList) {
          //noinspection unchecked
          myMap.put(key, new ValueList<>((ValueList<? extends V>)otherValue));
        }
        else {
          myMap.put(key, otherValue);
        }
      }
      else if (myValue instanceof ValueList) {
        //noinspection unchecked
        List<V> myListValue = (ValueList<V>)myValue;
        if (otherValue instanceof ValueList) {
          //noinspection unchecked
          myListValue.addAll((ValueList<? extends V>)otherValue);
        }
        else {
          //noinspection unchecked
          myListValue.add((V)otherValue);
        }
      }
      else {
        if (otherValue instanceof ValueList) {
          //noinspection unchecked
          List<V> otherListValue = (ValueList<V>)otherValue;
          List<V> newList = new ValueList<>(otherListValue.size() + 1);
          //noinspection unchecked
          newList.add((V)myValue);
          newList.addAll(otherListValue);
          myMap.put(key, newList);
        }
        else {
          List<Object> newList = new ValueList<>();
          newList.add(myValue);
          newList.add(otherValue);
          myMap.put(key, newList);
        }
      }
    }
  }

  // marker class to distinguish multi-values from single values in case client want to store collections as values.
  protected static final class ValueList<V> extends ArrayList<V> {
    public ValueList() {
    }

    public ValueList(int initialCapacity) {
      super(initialCapacity);
    }

    public ValueList(@NotNull Collection<? extends V> c) {
      super(c);
    }
  }

  public static @NotNull Class<? extends List<?>> getValueListClass() {
    //noinspection unchecked,rawtypes
    return (Class)MostlySingularMultiMap.ValueList.class;
  }

  public static @NotNull List<?> createValueList() {
    return new MostlySingularMultiMap.ValueList<>();
  }

  private static final class EmptyMap extends MostlySingularMultiMap<Object, Object> {
    static final MostlySingularMultiMap<?,?> EMPTY = new EmptyMap();

    private EmptyMap() {
      super(Collections.emptyMap());
    }

    @Override
    public void add(@NotNull Object key, @NotNull Object value) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean remove(@NotNull Object key, @NotNull Object value) {
      throw new IncorrectOperationException();
    }

    @Override
    public void clear() {
      throw new IncorrectOperationException();
    }
  }
}
