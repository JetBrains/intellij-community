// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.indexing.impl.IndexDebugProperties;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

/**
 *
 */
public class IntToIntBtreeTest {

  private static final int ENOUGH_KEYS = 1 << 22;

  private static final int PAGE_SIZE = 1 << 15;
  private static final StorageLockContext LOCK_CONTEXT = new StorageLockContext(true, true);

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private IntToIntBtree bTree;
  private Int2IntOpenHashMap generatedKeyValues;

  @BeforeClass
  public static void beforeClass() throws Exception {
    IndexDebugProperties.DEBUG = true;
  }

  @Before
  public void setUp() throws Exception {
    final File file = temporaryFolder.newFile("btree");
    LOCK_CONTEXT.writeLock().lock();

    bTree = new IntToIntBtree(PAGE_SIZE, file.toPath(), LOCK_CONTEXT, /*createAnew: */ true);
    generatedKeyValues = generateKeyValues(ENOUGH_KEYS);
  }

  @After
  public void tearDown() throws Exception {
    try {
      if (bTree != null) {
        bTree.doClose();
      }
    }finally {
      LOCK_CONTEXT.writeLock().unlock();
    }
  }


  @Test
  public void allKeysPutIntoBTreeCouldBeFoundBackWithAssociatedValues() throws IOException {
    for (Map.Entry<Integer, Integer> e : generatedKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }

    final int[] valueHolder = new int[1];
    final Int2IntMap.FastEntrySet entries = generatedKeyValues.int2IntEntrySet();
    final ObjectIterator<Int2IntMap.Entry> iterator = entries.fastIterator();
    while (iterator.hasNext()) {
      final Int2IntMap.Entry e = iterator.next();
      final int key = e.getIntKey();
      final int expectedValue = e.getIntValue();

      final boolean found = bTree.get(key, valueHolder);

      assertTrue("key[" + key + "] should be found in btree", found);
      assertEquals("key[" + key + "] should be mapped to value[" + expectedValue + "] by btree",
                   expectedValue,
                   valueHolder[0]);
    }
  }

  @Test
  public void allKeysValuesPutIntoBTreeCouldBeReadBackWithProcessMappings() throws IOException {
    for (Map.Entry<Integer, Integer> e : generatedKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }

    final Int2IntOpenHashMap bTreeContent = new Int2IntOpenHashMap(generatedKeyValues.size());
    bTree.processMappings(new IntToIntBtree.KeyValueProcessor() {
      @Override
      public boolean process(final int key,
                             final int value) throws IOException {
        bTreeContent.put(key, value);
        return true;
      }
    });
    assertEquals(
      "Keys-values read from bTree should be the same as were put into bTree before",
      generatedKeyValues,
      bTreeContent
    );
  }


  @Test
  public void keys_Not_PutIntoBTreeCould_Not_BeFoundBack() throws IOException {
    for (Map.Entry<Integer, Integer> e : generatedKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }

    final int[] valueHolder = new int[1];
    final ThreadLocalRandom current = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_KEYS; i++) {
      final int key = nextKeyNotContainedInTree(current);
      final boolean found = bTree.get(key, valueHolder);

      assertFalse("key[" + key + "] should NOT be in BTree, but it is found, and mapped to value[" + valueHolder[0] + "]",
                  found);
    }
  }

  @Test
  public void overwrittenValuesAreReadBackAsWritten() throws IOException {
    for (Map.Entry<Integer, Integer> e : generatedKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }

    final int[] allKeys = generatedKeyValues.keySet().intStream().toArray();
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final int[] valueHolder = new int[1];

    //overwrite random keys with new values: do enough ENOUGH_KEYS * 4 turns so a lot of keys
    // will be overwritten more than once
    for (int i = 0; i < ENOUGH_KEYS * 4; i++) {
      final int keyIndex = rnd.nextInt(allKeys.length);
      final int keyToOverwrite = allKeys[keyIndex];
      final int newValue = rnd.nextInt();
      generatedKeyValues.put(keyToOverwrite, newValue);
      bTree.put(keyToOverwrite, newValue);

      bTree.get(keyToOverwrite, valueHolder);
      assertEquals(
        "Value just written must be read back as-is",
        newValue,
        valueHolder[0]
      );
    }

    //check final state: bTree content is same as generatedKeyValues content:
    final Int2IntMap.FastEntrySet entries = generatedKeyValues.int2IntEntrySet();
    final ObjectIterator<Int2IntMap.Entry> iterator = entries.fastIterator();
    while (iterator.hasNext()) {
      final Int2IntMap.Entry e = iterator.next();
      final int key = e.getIntKey();
      final int expectedValue = e.getIntValue();

      final boolean found = bTree.get(key, valueHolder);

      assertTrue("key[" + key + "] should be found in btree", found);
      assertEquals("key[" + key + "] should be mapped to value[" + expectedValue + "] by btree",
                   expectedValue,
                   valueHolder[0]);
    }
  }

  //TODO RC: check all properties are kept after store/load


  /* ================================= INFRASTRUCTURE: ================================================================== */

  private int nextKeyNotContainedInTree(final ThreadLocalRandom current) {
    for (int i = 0; i < 1024; i++) {
      final int key = current.nextInt();
      if (!generatedKeyValues.containsKey(key)) {
        return key;
      }
    }
    //avoid test hangs in pathological cases:
    throw new IllegalStateException("Something is wrong with 1024 random ints all contained in generatedKeyValues");
  }


  private Int2IntOpenHashMap generateKeyValues(final int keysCount) {
    final Int2IntOpenHashMap keyValues = new Int2IntOpenHashMap(keysCount);
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < keysCount; i++) {
      final int key = rnd.nextInt();
      final int value = rnd.nextInt();
      keyValues.put(key, value);
    }
    return keyValues;
  }
}