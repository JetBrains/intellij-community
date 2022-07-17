// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.util.containers.hash.LongLinkedHashMap;
import org.junit.Assert;
import org.junit.Test;

public class LongLinkedHashMapTest {

  @Test
  public void testPutAndGet() {
    LongLinkedHashMap<String> map = new LongLinkedHashMap<>(10, 1, true);
    Assert.assertNull(map.get(123));
    Assert.assertNull(map.put(123, "123"));
    Assert.assertEquals("123", map.get(123));
  }

  @Test
  public void testClear() {
    LongLinkedHashMap<String> map = new LongLinkedHashMap<>(10, 1, true);

    map.put(1, "1");
    map.put(2, "2");
    map.put(3, "3");
    map.put(4, "4");
    Assert.assertEquals(4, map.size());
    map.clear();
    Assert.assertEquals(0, map.size());
    Assert.assertTrue(map.isEmpty());
  }

  @Test
  public void testRemoveEldest() {
    LongLinkedHashMap<String> map = new LongLinkedHashMap<>(10, 1, true) {
      @Override
      protected boolean removeEldestEntry(Entry<String> eldest) {
        return size() > 3;
      }
    };

    map.put(1, "1");
    map.put(2, "2");
    map.put(3, "3");
    map.put(4, "4");
    Assert.assertEquals(3, map.size());
    Assert.assertNull(map.get(1));
    Assert.assertEquals("2", map.get(2));
    Assert.assertEquals("3", map.get(3));
    Assert.assertEquals("4", map.get(4));

  }
}
