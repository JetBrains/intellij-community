// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProvider;

public class DefaultIndexStorageLayoutProviderTest extends IndexStorageLayoutProviderTestBase {

  public static final int INPUTS_COUNT_TO_TEST_WITH = 10_000;

  public DefaultIndexStorageLayoutProviderTest() {
    super(new DefaultIndexStorageLayoutProvider(), INPUTS_COUNT_TO_TEST_WITH);
  }
}
