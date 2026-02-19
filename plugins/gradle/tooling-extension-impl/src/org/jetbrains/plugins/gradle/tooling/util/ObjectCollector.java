// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ObjectCollector} provides convenient way to avoid expensive operations with the same object when it passed in multiple times.
 * <p>
 *   E.g. during the serialization the whole object can be written once
 *   and other references to the object can be written using a single int "objectId" value
 * </p>
 *
 * @author Vladislav.Soroka
 */
public final class ObjectCollector<T, E extends Exception> {
  private final TObjectIntHashMap<T> myObjectMap;
  private int instanceCounter = 0;

  public ObjectCollector() {
    //noinspection unchecked
    this(TObjectHashingStrategy.CANONICAL);
  }

  public ObjectCollector(TObjectHashingStrategy<T> hashingStrategy) {
    myObjectMap = new TObjectIntHashMap<>(hashingStrategy);
  }

  public void add(@NotNull T object, @NotNull Processor<? extends E> consumer) throws E {
    int objectId = myObjectMap.get(object);
    boolean isNew = objectId == 0;
    if (isNew) {
      int newId = ++instanceCounter;
      myObjectMap.put(object, newId);
      objectId = newId;
    }
    consumer.process(isNew, objectId);
  }

  public interface Processor<E extends Exception> {
    void process(boolean isAdded, int objectId) throws E;
  }
}
