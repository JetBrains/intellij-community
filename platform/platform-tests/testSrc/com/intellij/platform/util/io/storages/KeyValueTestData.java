// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

/// Provides fixture operations for [com.intellij.util.io.KeyValueStore] test scenarios
///
/// The challenge with property-based tests of _durable_ [com.intellij.util.io.KeyValueStore]s is: because the
/// Store is durable, small and large worksets may go different code paths => to really stress most branches in
/// the code, one needs to generate quite a lot of key-values -- ideally, more than fit into a heap. But that
/// many key-values trigger OutOfMemory quite quickly.
///
/// So the trick:
/// - generate not key-values, but a memory-frugal `substrate` (int);
/// - introduce a 'substrate decoder', [keyValue]: `[substrate: int -> (key, value)]`;
/// - keep only the `substrate`s in memory (=frugal) and convert the substrate to the [Map.Entry] as-needed
///
/// This way the generated [Map.Entry] is only transiently in memory, and not fill up the heap.
public final class KeyValueTestData<K, V> {
  private static final int ENOUGH_KEY_VALUES = 1_000_000;

  /// Decodes a substrate into a simple string key-value pair
  public static final IntFunction<Entry<String, String>> STRING_SUBSTRATE_DECODER = substrate -> {
    var key = String.valueOf(substrate);
    if (substrate % 1024 == 1023) {
      var veryLongKey = StringUtil.repeat(key, 1024);
      return Map.entry(
        veryLongKey,
        substrate + "." + veryLongKey
      );
    }

    return Map.entry(
      key,
      substrate + "." + substrate
    );
  };

  private final int[] substrate;
  private final IntFunction<? extends Entry<K, V>> substrateDecoder;

  KeyValueTestData(int @NotNull [] substrate,
                   @NotNull IntFunction<? extends Entry<K, V>> substrateDecoder) {
    this.substrate = substrate;
    this.substrateDecoder = substrateDecoder;
  }

  public int @NotNull [] substrate() {
    return substrate;
  }

  public @NotNull Entry<K, V> keyValue(int substrate) {
    return substrateDecoder.apply(substrate);
  }

  public V differentValue(int substrate, int salt) {
    var originalValue = keyValue(substrate).getValue();
    var differentSubstrate = substrate + salt;
    var differentValue = keyValue(differentSubstrate).getValue();
    if (Objects.equals(originalValue, differentValue)) {
      throw new AssertionError("value(" + substrate + ")(=" + originalValue + ") happens to be == " +
                               "value(" + differentSubstrate + ")(=" + differentValue + ")");
    }
    return differentValue;
  }

  static int @NotNull [] generateSubstrate() {
    return ThreadLocalRandom.current().ints(0, Integer.MAX_VALUE)
      .distinct()
      .limit(ENOUGH_KEY_VALUES)
      .toArray();
  }
}
