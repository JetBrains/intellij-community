// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class IntObjectHashMapTest {
  @Test
  public void test() {
    IntObjectHashMap<String> map = new IntObjectHashMap<>(size -> new String[size]);
    Int2ObjectOpenHashMap<String> checkMap = new Int2ObjectOpenHashMap<>();
    Int2ObjectOpenHashMap<String> dupesMap = new Int2ObjectOpenHashMap<>();
    Random random = new Random();
    for(int i = 0; i < 1000000; ++i) {
      int key = random.nextInt();
      String value = String.valueOf(random.nextInt());

      if (!checkMap.containsKey(key)) {
        map.put(key, value);
        checkMap.put(key, value);
        assertEquals(map.size(), checkMap.size());
        assertThat(map.get(key)).isEqualTo(value);
      }
      else {
        dupesMap.put(key, value);
      }
    }

    dupesMap.put(0, "random string");

    for (Int2ObjectMap.Entry<String> entry : dupesMap.int2ObjectEntrySet()) {
      int k = entry.getIntKey();
      String v = entry.getValue();
      checkMap.put(k, v);
      map.put(k, v);
      assertEquals(map.size(), checkMap.size());
      assertEquals(v, map.get(k));
    }

    String value = "random string2";
    checkMap.put(0, value);
    map.put(0, value);

    for (Int2ObjectMap.Entry<String> entry : checkMap.int2ObjectEntrySet()) {
      assertEquals(entry.getValue(), map.get(entry.getIntKey()));
    }
    assertThat(map.size()).isEqualTo(checkMap.size());
  }
}