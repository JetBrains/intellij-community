// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.containers.ChangeBufferingList;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.EnumeratorStringDescriptor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

public class ValueContainersTest extends TestCase {
  public void testNullValueSingleId() {
    runSimpleAddRemoveIteration(new Void[] {null}, new int[][]{{5}});
  }

  public void testNullValueSameIdTrice() {
    runSimpleAddRemoveIteration(new Void[] {null}, new int[][]{{5, 5, 5}});
  }

  public void testOneValueManyId() {
    runSimpleAddRemoveIteration(new String[] {"value"}, new int[][]{{2, 3, 4}});
  }

  public void testManyValuesManyId() {
    runSimpleAddRemoveIteration(new Integer[] { 25, 33, 77}, new int[][]{{ 10, 20, 30}, { 11, 22 }, {44}});
  }

  public void testMixedValueContainer() {
    ValueContainerImpl<String> container = ValueContainerImpl.createNewValueContainer();

    container.addValue(294005, "com");
    container.addValue(294001, "com");
    container.addValue(294010, null);
    container.removeValue(294010, null);
    container.addValue(294001, "com");
    container.removeValue(294005, "com");
    container.removeValue(294001, "com");

    assertEquals("Actual container: " + container, 0, container.size());
  }

  public void testValueContainerForTroveMap() throws IOException {
    class MyValueExternalizer implements DataExternalizer<Map<String, String>> {
      @Override
      public void save(@NotNull DataOutput out, Map<String, String> value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());
        for (Map.Entry<String, String> entry : value.entrySet()) {
          EnumeratorStringDescriptor.INSTANCE.save(out, entry.getKey());
          EnumeratorStringDescriptor.INSTANCE.save(out, entry.getValue());
        }
      }

      @Override
      public Map<String, String> read(@NotNull DataInput in) throws IOException {
        int size = DataInputOutputUtil.readINT(in);
        Map<String, String> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
          map.put(EnumeratorStringDescriptor.INSTANCE.read(in), EnumeratorStringDescriptor.INSTANCE.read(in));
        }
        return map;
      }
    }
    ValueContainerImpl<Map<String, String>> container = ValueContainerImpl.createNewValueContainer();
    container.addValue(111, new HashMap<>());
    container.addValue(222, new HashMap<>());
    Map<String, String> value = new HashMap<>();
    value.put("some", "awesome");
    container.addValue(333, value);

    MyValueExternalizer externalizer = new MyValueExternalizer();
    BufferExposingByteArrayOutputStream os = new BufferExposingByteArrayOutputStream();
    container.saveTo(new DataOutputStream(os), externalizer);

    ValueContainerImpl<Map<String, String>> container2 = ValueContainerImpl.createNewValueContainer();
    container2.readFrom(new DataInputStream(new ByteArrayInputStream(os.toByteArray())), new MyValueExternalizer(), ValueContainerInputRemapping.IDENTITY);
    AtomicInteger count = new AtomicInteger();
    container2.forEach((id, value1) -> {
      count.incrementAndGet();
      if (id == 111 || id == 222) {
        assertEquals(0, value1.size());
      }
      else if (id == 333) {
        assertEquals(1, value1.size());
        assertEquals("some", value1.keySet().iterator().next());
        assertEquals("awesome", value1.values().iterator().next());
      }
      return true;
    });
    assertEquals(3, count.get());
  }

  private static <T> void runSimpleAddRemoveIteration(T[] values, int[][] inputIds) {
    HashMap<T, IntList> valueToIdList = new HashMap<>();
    ValueContainerImpl<T> container = ValueContainerImpl.createNewValueContainer();

    for(int i = 0; i < values.length; ++i) {
      IntList list = new IntArrayList(inputIds.length);
      IntSet set = new IntOpenHashSet(inputIds.length);
      T value = values[i];

      for (int inputId : inputIds[i]) {
        container.addValue(inputId, value);
        if (set.add(inputId)) list.add(inputId);
      }
      list.sort(null);
      valueToIdList.put(value, list);
    }

    InvertedIndexValueIterator<T> valueIterator = container.getValueIterator();

    //noinspection unused
    for (T unusedValue : values) {
      assertTrue(valueIterator.hasNext());

      T value = valueIterator.next();
      IntList list = valueToIdList.get(value);
      assertNotNull(list);

      Object object = valueIterator.getFileSetObject();

      if (list.size() == 1) {
        assertEquals(Integer.valueOf(list.getInt(0)), object);
      }
      else {
        assertTrue(object instanceof ChangeBufferingList);
      }

      IntPredicate predicate = valueIterator.getValueAssociationPredicate();

      for (int inputId : list.toIntArray()) assertTrue(predicate.test(inputId));

      ValueContainer.IntIterator iterator = valueIterator.getInputIdsIterator();
      assertEquals(list.size(), iterator.size());

      for (int ignore : list.toIntArray()) {
        assertTrue(iterator.hasNext());
        assertTrue(list.contains(iterator.next()));
      }

      assertFalse(iterator.hasNext());
    }

    assertFalse(valueIterator.hasNext());

    valueToIdList.forEach((key, value) -> {
      for (int inputId : value.toIntArray()) {
        container.removeAssociatedValue(inputId);
      }
    });

    assertEquals(0, container.size());
    valueIterator = container.getValueIterator();
    assertFalse(valueIterator.hasNext());
  }
}
