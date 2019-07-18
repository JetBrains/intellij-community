// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

/**
 * A Disposable wrapper that is automatically disposed whenever an associated child disposable
 * is garbage-collected. Note that the associated disposable is NOT disposed when it's garbage-collected,
 * only the wrapper itself is removed from the Disposer tree.
 *
 * @author eldar
 */
public class WeakReferenceDisposableWrapper extends WeakReferenceDisposable<Disposable> {
  public WeakReferenceDisposableWrapper(@NotNull Disposable referent) {
    super(referent);
  }

  @Override
  protected void disposeReferent(@NotNull Disposable referent) {
    Disposer.dispose(referent);
  }
}
