// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Weak hash set.
 * Null keys are NOT allowed
 */
final class WeakHashSet<T> extends AbstractSet<T> implements ReferenceQueueable {
  private final Set<MyRef<T>> set = new HashSet<>();
  private final ReferenceQueue<T> queue = new ReferenceQueue<>();

  private static final class MyRef<T> extends WeakReference<T> {
    private final int myHashCode;

    MyRef(@NotNull T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      myHashCode = referent.hashCode();
    }

    @Override
    public int hashCode() {
      return myHashCode; // has to be stable even in presence of GC
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MyRef)) return false;
      MyRef<?> otherRef = (MyRef<?>)obj;
      return Comparing.equal(otherRef.get(), get());
    }
  }

  @Override
  public @NotNull Iterator<T> iterator() {
    return ContainerUtil.filterIterator(ContainerUtil.mapIterator(set.iterator(), Reference::get), Objects::nonNull);
  }

  @Override
  public int size() {
    return set.size();
  }

  @Override
  public boolean add(@NotNull T t) {
    processQueue();
    MyRef<T> ref = new MyRef<>(t, queue);
    return set.add(ref);
  }

  @Override
  public boolean remove(@NotNull Object o) {
    processQueue();
    //noinspection unchecked
    return set.remove(new MyRef<>((T)o, null));
  }

  @Override
  public boolean contains(@NotNull Object o) {
    processQueue();
    //noinspection unchecked
    return set.contains(new MyRef<>((T)o, null));
  }

  @Override
  public void clear() {
    set.clear();
  }

  @Override
  public boolean processQueue() {
    boolean processed = false;
    MyRef<T> ref;
    //noinspection unchecked
    while ((ref = (MyRef<T>)queue.poll()) != null) {
      // could potentially remove irrelevant gced MyRef entry with the same hashCode, but it's ok,
      // because the removal of that other MyRef down the queue will lead to removal of this entry `ref` later.
      processed |= set.remove(ref);
    }
    return processed;
  }
}
