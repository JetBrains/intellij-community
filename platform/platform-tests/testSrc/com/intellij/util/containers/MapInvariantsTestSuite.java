// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.util.Factory;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

@RunWith(Parameterized.class)
public class MapInvariantsTestSuite {
  private final Factory<Map<Object, Object>> factory;

  public MapInvariantsTestSuite(@SuppressWarnings("unused") String name, Factory<Map<Object, Object>> fac) {
    factory = fac;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> parameters() {
    return Arrays.asList(
      name("Our HashMap", com.intellij.util.containers.hash.HashMap::new),
      name("Our ConcurrentHashMap", () -> ConcurrentCollectionFactory.createMap(ContainerUtil.canonicalStrategy())),
      name("Our LinkedHashMap", LinkedHashMap::new),
      name("ConcurrentFactoryMap (on ConcurrentHashMap)", () -> ConcurrentFactoryMap.createMap(k -> "val_" + k))
    );
  }

  private static Object[] name(String name, Factory<Map<Object, Object>> fac) {
    return ContainerUtil.ar(name, fac);
  }

  @Test
  public void checkNullKeys() {
    Map<Object, Object> map = factory.create();
    String val = "val";
    Assume.assumeTrue("Does not support null keys", isSupported(() -> map.put(null, val)));
    Assert.assertEquals(1, map.size());
    Assert.assertTrue("Null key not found", map.containsKey(null));
    Assert.assertEquals(1, map.size());
    Assert.assertEquals("Val differs", val, map.get(null));
    Assert.assertEquals(1, map.size());
    Assert.assertEquals("Removed val differs", val, map.remove(null));
    Assert.assertEquals(0, map.size());
    map.put(null, val);
    Assert.assertTrue("Removed val differs", map.remove(null, val));
    Assert.assertEquals(0, map.size());
  }

  @Test
  public void checkNullVals() {
    Map<Object, Object> map = factory.create();
    String key = "key";
    Assume.assumeTrue("Does not support null values", isSupported(() -> map.put(key, null)));
    Assert.assertEquals(1, map.size());
    Assert.assertTrue("Null val not found", map.containsValue(null));
    Assert.assertTrue("Null val not found by key", map.containsKey(key));
    Assert.assertEquals(1, map.size());
    Assert.assertNull("Val differs", map.get(key));
    Assert.assertEquals(1, map.size());
    Assert.assertNull("Removed val differs", map.remove(key));
    Assert.assertEquals(0, map.size());
    map.put(key, null);
    Assert.assertTrue("Removed val differs", map.remove(key, null));
    Assert.assertEquals(0, map.size());
  }

  @Test
  public void checkEntrySet() {
    checkEntrySetImpl("key", "val");
  }

  @Test
  public void checkEntrySetNullKey() {
    checkEntrySetImpl(null, "val");
  }

  @Test
  public void checkEntrySetNullValue() {
    checkEntrySetImpl("key", null);
  }

  private void checkEntrySetImpl(String key, String val) {
    Map<Object, Object> map = factory.create();
    Assume.assumeTrue("Adding (" + key + ", " + val + ") not supported", isSupported(() -> map.put(key, val)));
    Set<Map.Entry<Object, Object>> entries = map.entrySet();
    Assert.assertEquals(1, entries.size());
    Assume.assumeTrue("Entry set is readonly", isSupported(() -> entries.clear()));
    Assert.assertEquals(0, entries.size());
    Assert.assertEquals(0, map.size());
    map.put(key, val);
    Assert.assertEquals(1, entries.size());
    Iterator<Map.Entry<Object, Object>> it = entries.iterator();
    Map.Entry<Object, Object> o = it.next();
    Assert.assertEquals(key, o.getKey());
    Assert.assertEquals(val, o.getValue());
    it.remove();
    Assert.assertEquals(0, entries.size());
    Assert.assertEquals(0, map.size());
  }

  @Test
  public void checkKeySet() {
    checkKeySetImpl("key");
  }

  @Test
  public void checkKeySetNullKey() {
    checkKeySetImpl(null);
  }

  private void checkKeySetImpl(String key) {
    String val = "val";
    Map<Object, Object> map = factory.create();
    Assume.assumeTrue("Adding (" + key + ", " + val + ") not supported", isSupported(() -> map.put(key, val)));
    Set<Object> keys = map.keySet();
    Assert.assertTrue("Key not found", keys.contains(key));
    Assert.assertEquals(1, keys.size());
    Assume.assumeTrue("Key set is readonly", isSupported(() -> keys.clear()));
    Assert.assertEquals(0, keys.size());
    Assert.assertEquals(0, map.size());
    map.put(key, val);
    Assert.assertEquals(1, keys.size());
    Iterator<?> it = keys.iterator();
    Assert.assertEquals(key, it.next());
    it.remove();
    Assert.assertEquals(0, keys.size());
    Assert.assertEquals(0, map.size());
    map.put(key, val);
    keys.remove(key);
    Assert.assertEquals(0, keys.size());
    Assert.assertEquals(0, map.size());
  }

  @Test
  public void checkValues() {
    checkValuesImpl("val");
  }

  @Test
  public void checkValuesNullValue() {
    checkValuesImpl(null);
  }

  private void checkValuesImpl(String val) {
    String key = "key";
    Map<Object, Object> map = factory.create();
    Assume.assumeTrue("Adding (" + key + ", " + val + ") not supported", isSupported(() -> map.put(key, val)));
    Collection<Object> values = map.values();
    Assert.assertTrue("Value not found", values.contains(val));
    Assert.assertEquals(1, values.size());
    Assume.assumeTrue("Values is readonly", isSupported(() -> values.clear()));
    Assert.assertEquals(0, values.size());
    Assert.assertEquals(0, map.size());
    map.put(key, val);
    Assert.assertEquals(1, values.size());
    Iterator<?> it = values.iterator();
    Assert.assertEquals(val, it.next());
    it.remove();
    Assert.assertEquals(0, values.size());
    Assert.assertEquals(0, map.size());
    map.put(key, val);
    values.remove(val);
    Assert.assertEquals(0, values.size());
    Assert.assertEquals(0, map.size());
  }

  private static boolean isSupported(Runnable op) {
    try {
      op.run();
      return true;
    }
    catch (RuntimeException e) {
      return false;
    }
  }
}
