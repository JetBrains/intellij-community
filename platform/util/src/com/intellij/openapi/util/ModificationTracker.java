// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @see SimpleModificationTracker
 */
@FunctionalInterface
public interface ModificationTracker {
  
  long getModificationCount();

  ModificationTracker EVER_CHANGED = new ModificationTracker() {
    private final AtomicLong myCounter = new AtomicLong();

    @Override
    public long getModificationCount() {
      return myCounter.getAndIncrement();
    }
  };

  ModificationTracker NEVER_CHANGED = () -> 0;
}
