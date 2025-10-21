// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An interface used to support simple tracking of changes to some <i>state</i> (understood in the broadest sense) over time.
 * Whenever the state changes, the corresponding counter (of the {@code ModificationTracker} that tracks this state) is incremented.
 * <p>
 * Often used as a dependency of a {@link com.intellij.psi.util.CachedValue CachedValue}
 * (through {@link com.intellij.psi.util.CachedValueProvider.Result CachedValueProvider.Result}).
 * <p>
 * Implementations are strongly advised to clearly document what exactly is tracked
 * and when the modification counter (aka "stamp") is incremented.
 *
 * @see SimpleModificationTracker
 */
@FunctionalInterface
public interface ModificationTracker {

  long getModificationCount();

  /**
   * A modification tracker whose counter is incremented whenever it is queried.
   */
  ModificationTracker EVER_CHANGED = new ModificationTracker() {
    private final AtomicLong myCounter = new AtomicLong();

    @Override
    public long getModificationCount() {
      return myCounter.getAndIncrement();
    }
  };

  /**
   * A modification tracker whose counter is never incremented.
   */
  ModificationTracker NEVER_CHANGED = () -> 0;
}
