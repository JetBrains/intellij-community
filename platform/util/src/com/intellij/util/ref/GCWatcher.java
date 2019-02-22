// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ref;

import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * A utility to garbage-collect specified objects in tests. Create a GCWatcher using {@link #tracking} or {@link #fromClearedRef}
 * and then call {@link #tryGc()}. Please ensure that your test doesn't hold references to objects passed to {@link #tracking},
 * so, if you pass fields or local variables there, nullify them before calling {@link #tryGc()}.
 *
 */
@TestOnly
public class GCWatcher {
  private final ReferenceQueue<Object> myQueue = new ReferenceQueue<>();
  private final Set<Reference<?>> myReferences = ContainerUtil.newConcurrentSet();

  private GCWatcher(@NotNull Collection<?> objects) {
    for (Object o : objects) {
      if (o != null) {
        myReferences.add(new PhantomReference<>(o, myQueue));
      }
    }
  }

  @NotNull
  @Contract(pure = true)
  public static GCWatcher tracking(Object... objects) {
    return tracking(Arrays.asList(objects));
  }

  @NotNull
  @Contract(pure = true)
  public static GCWatcher tracking(@NotNull Collection<?> objects) {
    return new GCWatcher(objects);
  }

  /**
   * Create a GCWatcher from whatever is in the ref, then clear the ref.
   */
  @NotNull
  public static GCWatcher fromClearedRef(@NotNull Ref<?> ref) {
    GCWatcher result = tracking(ref.get());
    ref.set(null);
    return result;
  }

  private boolean isEverythingCollected() {
    while (true) {
      Reference<?> ref = myQueue.poll();
      if (ref == null) return myReferences.isEmpty();

      boolean removed = myReferences.remove(ref);
      assert removed;
    }
  }

  /**
   * Attempt to run garbage collector repeatedly until all the objects passed when creating this GCWatcher are GC-ed. If that's impossible,
   * this method gives up after some time.
   */
  public void tryGc() {
    GCUtil.allocateTonsOfMemory(this::isEverythingCollected);
  }

}
