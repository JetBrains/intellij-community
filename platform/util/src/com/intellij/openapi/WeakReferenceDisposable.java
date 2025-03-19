// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * A Disposable wrapper that is automatically disposed whenever an associated object is garbage-collected.
 *
 * @author eldar
 */
@ApiStatus.Internal
public abstract class WeakReferenceDisposable<T> extends WeakReference<T> implements Disposable {
  private static final ReferenceQueue<Object> ourRefQueue = new ReferenceQueue<>();

  public WeakReferenceDisposable(@NotNull T referent) {
    super(referent, ourRefQueue);
    reapCollectedRefs();
  }

  @Override
  public final void dispose() {
    final T referent = get();
    if (referent == null) return;
    clear();
    disposeReferent(referent);
  }

  protected abstract void disposeReferent(@NotNull T referent);

  private static void reapCollectedRefs() {
    while (true) {
      final Reference<?> ref = ourRefQueue.poll();
      if (ref == null) break;
      if (!(ref instanceof WeakReferenceDisposable)) continue;
      Disposer.dispose((Disposable)ref);  // the referent is gone, remove from the Disposer tree
    }
  }
}
