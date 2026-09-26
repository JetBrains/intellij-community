// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class IndexStorageUtil {
  public static <K, V> @NotNull Map<K, V> createKeyDescriptorHashedMap(@NotNull KeyDescriptor<? super K> keyDescriptor) {
    return new Object2ObjectOpenCustomHashMap<>(new Hash.Strategy<K>() {
      @Override
      public int hashCode(@Nullable K o) {
        return o == null ? 0 : keyDescriptor.getHashCode(o);
      }

      @Override
      public boolean equals(@Nullable K a, @Nullable K b) {
        return a == b || (a != null && b != null && keyDescriptor.isEqual(a, b));
      }
    });
  }

  public static <K> @NotNull HashingStrategy<K> adaptKeyDescriptorToStrategy(@NotNull KeyDescriptor<? super K> keyDescriptor) {
    return new HashingStrategy<K>() {
      @Override
      public int hashCode(@Nullable K o) {
        if (o == null) return 0;
        return keyDescriptor.getHashCode(o);
      }

      @Override
      public boolean equals(@Nullable K a, @Nullable K b) {
        if (a == null && b != null) return false;
        if (b == null && a != null) return false;
        return keyDescriptor.isEqual(a, b);
      }
    };
  }
}
