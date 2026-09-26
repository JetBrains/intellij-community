// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link ObjectCollector} provides convenient way to avoid expensive operations with the same object when it passed in multiple times.
 * <p>
 * E.g. during the serialization the whole object can be written once
 * and other references to the object can be written using a single int "objectId" value
 * </p>
 *
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class ObjectCollector<T, E extends Exception> {

  private final @NotNull Map<HasherHook, Integer> objects = new HashMap<>();
  private final @NotNull ObjectCollector.Hasher<? super T> hasher;

  private int instanceCounter = 0;

  public ObjectCollector() {
    this(new DefaultHasher());
  }

  public ObjectCollector(@NotNull ObjectCollector.Hasher<? super T> hashingStrategy) {
    this.hasher = hashingStrategy;
  }

  public void add(@NotNull T object, @NotNull Processor<? extends E> consumer) throws E {
    HasherHook hook = new HasherHook(object);
    int objectId = objects.getOrDefault(hook, 0);
    boolean isNew = objectId == 0;
    if (isNew) {
      int newId = ++instanceCounter;
      objects.put(hook, newId);
      objectId = newId;
    }
    consumer.process(isNew, objectId);
  }

  public interface Processor<E extends Exception> {
    void process(boolean isAdded, int objectId) throws E;
  }

  public interface Hasher<T> {

    int computeHashCode(T object);

    boolean equals(T o1, T o2);
  }

  private static class DefaultHasher implements Hasher<Object> {
    @Override
    public int computeHashCode(Object object) {
      return Objects.hashCode(object);
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      return Objects.equals(o1, o2);
    }
  }

  private class HasherHook {

    private final String HASHER_HOOK_CLASS_FQN = HasherHook.class.getCanonicalName();
    private final T delegate;

    private HasherHook(T delegate) { this.delegate = delegate; }

    @Override
    public int hashCode() {
      return hasher.computeHashCode(delegate);
    }

    @Override
    public boolean equals(Object other) {
      if (!HASHER_HOOK_CLASS_FQN.equals(other.getClass().getCanonicalName())) {
        return false;
      }
      T unwrappedOther = ((HasherHook)other).delegate;
      if (!delegate.getClass().equals(unwrappedOther.getClass())) {
        return false;
      }
      return hasher.equals(delegate, unwrappedOther);
    }
  }
}
