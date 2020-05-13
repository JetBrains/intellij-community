/*
 * Copyright (C) 2017 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;

public class ObjectLinkedOpenHashSetTest {
  private static final int DEFAULT_INITIAL_SIZE = 16;
  private static final float DEFAULT_LOAD_FACTOR = 0.75f;
  private static final float FAST_LOAD_FACTOR = 0.5f;
  private static final float VERY_FAST_LOAD_FACTOR = 0.25f;
  private static final Random ourRandom = new Random(0);

  @NotNull
  private static String genKey() {
    return Integer.toBinaryString(ourRandom.nextInt());
  }

  private static void checkTable(@NotNull ObjectLinkedOpenHashSet<String> linkedOpenHashSet) {
    final Object[] key = linkedOpenHashSet.key;
    assertEquals(linkedOpenHashSet.n & -linkedOpenHashSet.n, linkedOpenHashSet.n);
    assertEquals(linkedOpenHashSet.n, key.length - 1);
    int n = linkedOpenHashSet.n;
    while (n-- != 0) {
      if (key[n] != null) assertTrue(linkedOpenHashSet.contains((String)key[n]));
    }

    if (linkedOpenHashSet.containsNull) assertTrue(linkedOpenHashSet.contains(null));
    if (!linkedOpenHashSet.containsNull) assertFalse(linkedOpenHashSet.contains(null));

    java.util.HashSet<String> linkedHashSet = new java.util.LinkedHashSet<>();
    for (int i = linkedOpenHashSet.size(); i-- != 0; ) {
      if (key[i] != null) assertTrue(linkedHashSet.add((String)key[i]));
    }
  }

  @SuppressWarnings({"ResultOfMethodCallIgnored", "unchecked"})
  private static void test(int size, float loadFactor) {
    ObjectLinkedOpenHashSet<String> linkedOpenHashSet = new ObjectLinkedOpenHashSet<>(DEFAULT_INITIAL_SIZE, loadFactor);

    // Fill hashSet with random data.
    java.util.Set<String> linkedHashSet = new java.util.LinkedHashSet<>();
    for (int i = 0; i < loadFactor * size; i++) {
      linkedHashSet.add(genKey());
    }
    linkedOpenHashSet.addAll(linkedHashSet);
    assertEquals(linkedHashSet, linkedOpenHashSet);
    checkTable(linkedOpenHashSet);

    // Check that linkedHashSet actually holds that data.
    Iterator<String> iterator = linkedHashSet.iterator();
    while (iterator.hasNext()) {
      String element = iterator.next();
      assertTrue(linkedOpenHashSet.contains(element));
    }

    // Check that linkedHashSet actually holds that data, but iterating on linkedHashSet.
    int count = 0;
    iterator = linkedOpenHashSet.iterator();
    while (iterator.hasNext()) {
      String element = iterator.next();
      count++;
      assertTrue(linkedHashSet.contains(element));
    }
    assertEquals(count, linkedHashSet.size());

    // Check that inquiries about random data give the same answer in linkedHashSet and hashSet.
    for (int i = 0; i < size; i++) {
      String key = genKey();
      assertEquals(linkedOpenHashSet.contains(key), linkedHashSet.contains(key));
    }

    //Check that addOrGet does indeed return the original instance, not a copy
    iterator = linkedOpenHashSet.iterator();
    while (iterator.hasNext()) {
      String element = iterator.next();
      String newString = new String(element);  // Make a new object!
      String element2 = linkedOpenHashSet.addOrGet(newString);
      assertSame(element, element2);
    }
    // Should not have modified the table
    assertEquals(linkedHashSet, linkedOpenHashSet);

    // Put and remove random data in linkedHashSet and hashSet, checking that the result is the same.
    for (int i = 0; i < 20 * size; i++) {
      String key = genKey();
      assertEquals(linkedOpenHashSet.add(key), linkedHashSet.add(key));
      key = genKey();
      assertEquals(linkedOpenHashSet.remove(key), linkedHashSet.remove(key));
    }
    assertEquals(linkedHashSet, linkedOpenHashSet);
    checkTable(linkedOpenHashSet);

    // Check that linkedHashSet actually holds that data.
    iterator = linkedHashSet.iterator();
    while (iterator.hasNext()) {
      String element = iterator.next();
      assertTrue(linkedOpenHashSet.contains(element));
    }

    // Check that linkedHashSet actually holds that data, but iterating on linkedHashSet.
    iterator = linkedOpenHashSet.iterator();
    while (iterator.hasNext()) {
      String element = iterator.next();
      assertTrue(linkedHashSet.contains(element));
    }

    assertEquals(new ObjectLinkedOpenHashSet<>(linkedOpenHashSet), linkedOpenHashSet);
    // Check cloning.
    assertEquals("Error: linkedHashSet does not equal linkedHashSet.clone()", linkedOpenHashSet, linkedOpenHashSet.clone());

    // Save and read linkedOpenHashSet.
    int hashCode = linkedOpenHashSet.hashCode();
    File file = new File("ObjectLinkedOpenHashSetTest");
    try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file))) {
      outputStream.writeObject(linkedOpenHashSet);
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file))) {
      linkedOpenHashSet = (ObjectLinkedOpenHashSet<String>)inputStream.readObject();
      file.delete();
    }
    catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
      System.exit(1);
    }
    assertEquals(hashCode, linkedOpenHashSet.hashCode());
    checkTable(linkedOpenHashSet);

    // Check that linkedHashSet actually holds that data, but iterating on linkedOpenHashSet.
    iterator = linkedOpenHashSet.iterator();
    while (iterator.hasNext()) {
      String element = iterator.next();
      assertTrue(linkedHashSet.contains(element));
    }

    // Put and remove random data in linkedHashSet and hashSet, checking that the result is the same.
    for (int i = 0; i < 20 * size; i++) {
      String key = genKey();
      assertEquals(linkedOpenHashSet.add(key), linkedHashSet.add(key));
      key = genKey();
      assertEquals(linkedOpenHashSet.remove(key), linkedHashSet.remove(key));
    }
    assertEquals(linkedHashSet, linkedOpenHashSet);

    // Take out of linkedOpenHashSet everything, and check that it is empty.
    iterator = linkedOpenHashSet.iterator();
    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
    assertTrue("Error: linkedHashSet is not empty (as it should be)", linkedOpenHashSet.isEmpty());
  }

  @Test
  public void testStrangeRetainAllCase() {
    List<Integer> initialElements =
      new SmartList<>(586, 940, 1086, 1110, 1168, 1184, 1185, 1191, 1196, 1229, 1237, 1241, 1277, 1282, 1284, 1299, 1308, 1309, 1310, 1314,
        1328, 1360, 1366, 1370, 1378, 1388, 1392, 1402, 1406, 1411, 1426, 1437, 1455, 1476, 1489, 1513, 1533, 1538, 1540, 1541, 1543, 1547,
        1548, 1551, 1557, 1568, 1575, 1577, 1582, 1583, 1584, 1588, 1591, 1592, 1601, 1610, 1618, 1620, 1633, 1635, 1653, 1654, 1655, 1660,
        1661, 1665, 1674, 1686, 1688, 1693, 1700, 1705, 1717, 1720, 1732, 1739, 1740, 1745, 1746, 1752, 1754, 1756, 1765, 1766, 1767, 1771,
        1772, 1781, 1789, 1790, 1793, 1801, 1806, 1823, 1825, 1827, 1828, 1829, 1831, 1832, 1837, 1839, 1844, 2962, 2969, 2974, 2990, 3019,
        3023, 3029, 3030, 3052, 3072, 3074, 3075, 3093, 3109, 3110, 3115, 3116, 3125, 3137, 3142, 3156, 3160, 3176, 3180, 3188, 3193, 3198,
        3207, 3209, 3210, 3213, 3214, 3221, 3225, 3230, 3231, 3236, 3240, 3247, 3261, 4824, 4825, 4834, 4845, 4852, 4858, 4859, 4867, 4871,
        4883, 4886, 4887, 4905, 4907, 4911, 4920, 4923, 4924, 4925, 4934, 4942, 4953, 4957, 4965, 4973, 4976, 4980, 4982, 4990, 4993, 6938,
        6949, 6953, 7010, 7012, 7034, 7037, 7049, 7076, 7094, 7379, 7384, 7388, 7394, 7414, 7419, 7458, 7459, 7466, 7467);

    List<Integer> retainElements = new SmartList<>(586);

    // Initialize both implementations with the same data
    ObjectLinkedOpenHashSet<Integer> instance = new ObjectLinkedOpenHashSet<>(initialElements);
    Set<Integer> referenceInstance = new LinkedHashSet<>(initialElements);

    instance.retainAll(retainElements);
    referenceInstance.retainAll(retainElements);
    assertEquals(referenceInstance, instance);
  }


  @Test
  public void test1() {
    test(1, DEFAULT_LOAD_FACTOR);
    test(1, FAST_LOAD_FACTOR);
    test(1, VERY_FAST_LOAD_FACTOR);
  }

  @Test
  public void test10() {
    test(10, DEFAULT_LOAD_FACTOR);
    test(10, FAST_LOAD_FACTOR);
    test(10, VERY_FAST_LOAD_FACTOR);
  }

  @Test
  public void test100() {
    test(100, DEFAULT_LOAD_FACTOR);
    test(100, FAST_LOAD_FACTOR);
    test(100, VERY_FAST_LOAD_FACTOR);
  }

  @Test
  public void test1000() {
    test(1000, DEFAULT_LOAD_FACTOR);
    test(1000, FAST_LOAD_FACTOR);
    test(1000, VERY_FAST_LOAD_FACTOR);
  }

  @Test
  public void testGet() {
    final ObjectLinkedOpenHashSet<String> linkedHashSet = new ObjectLinkedOpenHashSet<>();
    String a = "a";
    assertTrue(linkedHashSet.add(a));
    assertSame(a, linkedHashSet.get("a"));
    assertNull(linkedHashSet.get("b"));
  }
}