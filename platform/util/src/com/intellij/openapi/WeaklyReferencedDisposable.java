// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * A Disposable wrapper that is automatically disposed whenever an associated child disposable
 * is garbage-collected. Note that the associated disposable is NOT disposed when it's garbage-collected,
 * only the wrapper itself is removed from the Disposer tree.
 *
 * @author eldar
 */
public class WeaklyReferencedDisposable extends WeakReference<Disposable> implements Disposable {
  private static final ReferenceQueue<Disposable> ourRefQueue = new ReferenceQueue<Disposable>();

  public WeaklyReferencedDisposable(@NotNull Disposable disposable) {
    super(disposable, ourRefQueue);
    reapCollectedRefs();
  }

  @Override
  public void dispose() {
    final Disposable disposable = get();
    if (disposable == null) return;
    clear();
    Disposer.dispose(disposable);
  }

  private static void reapCollectedRefs() {
    while (true) {
      final Reference<? extends Disposable> ref = ourRefQueue.poll();
      if (ref == null) break;
      if (!(ref instanceof WeaklyReferencedDisposable)) continue;
      Disposer.dispose((Disposable)ref);  // child is gone, remove the ref from the Disposer tree
    }
  }

}
