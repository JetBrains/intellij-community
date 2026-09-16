// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.util.io.KeyValueStore;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.Map;
import java.util.function.IntFunction;

/// Provides the fixture operations for [KeyValueStore] test scenarios
///
/// Scenario methods receive [KeyValueTestData] through the registered JUnit parameter resolver.
/// The test data creates key-value pairs on demand to keep memory use low.
@ExtendWith(KeyValueTestDataParameterResolver.class)
public interface KeyValueStoreTestContext<K, V, S extends KeyValueStore<K, V>> {

  /// KeyValueStore to test
  @NotNull S storageUnderTest();

  /// @return decoder for test (key,value) pairs source ([KeyValueTestData]) injected in test
  ///         methods by [KeyValueTestDataParameterResolver] -- see [KeyValueTestData] for more info.
  @NotNull IntFunction<? extends Map.Entry<K, V>> keyValueSubstrateDecoder();

  @SuppressWarnings("UnusedReturnValue")
  @NotNull S reopenStorage() throws IOException;
}
