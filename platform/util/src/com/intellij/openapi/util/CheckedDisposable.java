// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link Disposable} which knows its own "disposed" status.
 * Usually you don't need this class if you properly registered your {@link Disposable} in disposable hierarchy via {@link Disposer#register(Disposable, Disposable)},
 * because then <ol>
 * <li>your Disposable would be disposed automatically along with its parent</li> and
 * <li>you wouldn't need to worry about potential race conditions when {@link #isDisposed()} is called during the object disposal process</li>
 * </ol>
 * <p>
 * If however you (reluctantly) do need this class, be aware of additional memory consumption for storing extra "isDisposed" information.
 * <p>
 * To obtain the instance of this class, use {@link Disposer#newCheckedDisposable()}
 */
@ApiStatus.NonExtendable
public interface CheckedDisposable extends Disposable {
  /**
   * @return true when this instance is disposed (i.e. {@link Disposer#dispose(Disposable)} was called on this,
   * or it was registered in the {@link Disposer} hierarchy with {@link Disposer#register(Disposable, Disposable)} and its parent was disposed)
   */
  boolean isDisposed();
}
