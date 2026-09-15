// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import org.jetbrains.annotations.NotNull;

public class InMemoryIntToMultiLongMapTest extends IntToMultiLongMapTestBase {
  @Override
  protected @NotNull IntToMultiLongMap createMap() {
    return new InMemoryIntToMultiLongMap();
  }
}
