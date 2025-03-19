// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.IntObjectHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public class IntObjectMap<T> {
  private final IntObjectHashMap<T> myObjectsMap = new IntObjectHashMap<>(new IntObjectHashMap.ArrayProducer<T[]>() {
    @Override
    public T[] produce(int s) {
      return (T[])(s == 0 ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : new Object[s]);
    }
  });

  public T computeIfAbsent(int objectID, @NotNull ObjectFactory<T> objectFactory) {
    T object = myObjectsMap.get(objectID);
    if (object == null) {
      object = objectFactory.newInstance();
      myObjectsMap.put(objectID, object);
      objectFactory.fill(object);
    }
    return object;
  }

  public interface ObjectFactory<T> {
    T newInstance();

    void fill(T object);
  }

  public abstract static class SimpleObjectFactory<T> implements ObjectFactory<T> {
    public abstract T create();

    @Override
    public T newInstance() {
      return create();
    }

    @Override
    public void fill(T object) { }
  }
}
