// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.FileAccessorCache.Handle;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;

import static java.lang.Integer.MIN_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileAccessorCacheTest {
  @Test
  public void keyWithMinValueHashCode() {
    var cache = new FileAccessorCache<Object, String>(20, 10) {
      @Override
      protected @NotNull String createAccessor(Object key) {
        return key.toString();
      }

      @Override
      protected void disposeAccessor(@NotNull String fileAccessor) {
      }
    };

    // String.hashCode() is Integer.MIN_VALUE on all platforms.
    var string = "polygenelubricants";
    assertEquals(MIN_VALUE, string.hashCode());
    try (Handle<String> handle = cache.get(string)) {
      assertEquals(string, handle.get());
    }

    cache.clear();
  }
}
