// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Internal
public final class RefValueHashMapUtil {
  public static @NotNull IncorrectOperationException pointlessContainsKey() {
    return new IncorrectOperationException("containsKey() makes no sense for weak/soft map because GC can clear the value any moment now");
  }

  public static @NotNull IncorrectOperationException pointlessContainsValue() {
    return new IncorrectOperationException("containsValue() makes no sense for weak/soft map because GC can clear the key any moment now");
  }

  public static void expungeStaleEntries(@NotNull Map<?, ?> map) {
    if (map instanceof ReferenceQueueable) {
      ((ReferenceQueueable)map).processQueue();
    }
    else if (map instanceof java.util.WeakHashMap) {
      // Calling the size() method forces cleansing of the WeakHashMap
      //noinspection ResultOfMethodCallIgnored
      map.size();
    }
  }
}
