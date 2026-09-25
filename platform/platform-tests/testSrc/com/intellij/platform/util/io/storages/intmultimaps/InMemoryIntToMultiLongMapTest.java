// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InMemoryIntToMultiLongMapTest extends IntToMultiLongMapTestBase {
  @Override
  protected @NotNull IntToMultiLongMap createMap() {
    return new InMemoryIntToMultiLongMap();
  }

  @Test
  void lookupAndModifyDiscardsChangesWhenProcessorThrows() throws Exception {
    var map = createMap();
    map.put(1, 10);
    map.put(1, 20);
    var firstValue = new long[]{NO_VALUE};

    assertThrows(IOException.class, () -> map.lookupAndModify(1, (oldValue, newValueRef) -> {
      if (firstValue[0] == NO_VALUE) {
        firstValue[0] = oldValue;
        newValueRef.set(NO_VALUE);
        return true;
      }
      throw new IOException("Test exception");
    }));

    assertEquals(firstValue[0], map.lookup(1, value -> value == firstValue[0]));
    assertEquals(2, map.size());
  }
}
