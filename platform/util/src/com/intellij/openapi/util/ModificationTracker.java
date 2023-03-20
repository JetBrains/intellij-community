// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

@FunctionalInterface
public interface ModificationTracker {
  long getModificationCount();

  ModificationTracker EVER_CHANGED = new ModificationTracker() {
    private long myCounter;

    @Override
    public long getModificationCount() {
      return myCounter++;
    }
  };
  ModificationTracker NEVER_CHANGED = () -> 0;
}
