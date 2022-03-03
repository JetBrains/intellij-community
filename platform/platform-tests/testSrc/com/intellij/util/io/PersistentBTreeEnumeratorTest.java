// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectCache;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class PersistentBTreeEnumeratorTest {
  private static final Logger LOG = Logger.getInstance(PersistentBTreeEnumeratorTest.class);

  private static final String COLLISION_1 = "";
  private static final String COLLISION_2 = "\u0000";
  private static final String UTF_1 = "\ue534";
  private static final String UTF_2 = StringUtil.repeatSymbol('a', 624);

  static class TestStringEnumerator extends PersistentBTreeEnumerator<String> {
    TestStringEnumerator(File file) throws IOException {
      super(file.toPath(), new EnumeratorStringDescriptor(), 4096);
    }
  }

  @Rule public TempDirectory tempDir = new TempDirectory();

  private TestStringEnumerator myEnumerator;
  private File myFile;

  @Before
  public void setUp() throws IOException {
    myFile = tempDir.newFile("persistent-trie");
    myEnumerator = new TestStringEnumerator(myFile);
  }

  @After
  public void tearDown() throws IOException {
    myEnumerator.close();
    IOUtil.deleteAllFilesStartingWith(myFile);
    assertFalse(myFile.exists());
  }

  @Test
  public void testAddEqualStrings() throws IOException {
    int index = myEnumerator.enumerate("IntelliJ IDEA");
    myEnumerator.enumerate("Just another string");
    assertEquals(index, myEnumerator.enumerate("IntelliJ IDEA"));
  }

  @Test
  public void testAddEqualStringsAndMuchGarbage() throws IOException {
    Map<Integer, String> strings = new HashMap<>(10001);
    String s = "IntelliJ IDEA";
    int index = myEnumerator.enumerate(s);
    strings.put(index, s);

    // clear strings and nodes cache
    for (int i = 0; i < 10000; ++i) {
      String v = i + "Just another string";
      int idx = myEnumerator.enumerate(v);
      assertEquals(v, myEnumerator.valueOf(idx));
      strings.put(idx, v);
    }

    for (Map.Entry<Integer, String> e : strings.entrySet()) {
      assertEquals((int)e.getKey(), myEnumerator.enumerate(e.getValue()));
    }

    Set<String> enumerated = new HashSet<>(myEnumerator.getAllDataObjects(null));
    assertEquals(new HashSet<>(strings.values()), enumerated);
  }

  @Test
  public void testCollision() throws IOException {
    int id1 = myEnumerator.enumerate(COLLISION_1);
    int id2 = myEnumerator.enumerate(COLLISION_2);
    assertNotEquals(id1, id2);

    assertEquals(COLLISION_1, myEnumerator.valueOf(id1));
    assertEquals(COLLISION_2, myEnumerator.valueOf(id2));
    assertEquals(ContainerUtil.set(COLLISION_1, COLLISION_2),
                 new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  @Test
  public void testCollision1() throws IOException {
    int id1 = myEnumerator.enumerate(COLLISION_1);

    assertEquals(id1, myEnumerator.tryEnumerate(COLLISION_1));
    assertEquals(PersistentEnumeratorBase.NULL_ID, myEnumerator.tryEnumerate(COLLISION_2));

    int id2 = myEnumerator.enumerate(COLLISION_2);
    assertNotEquals(id1, id2);

    assertEquals(id1, myEnumerator.tryEnumerate(COLLISION_1));
    assertEquals(id2, myEnumerator.tryEnumerate(COLLISION_2));
    assertEquals(PersistentEnumeratorBase.NULL_ID, myEnumerator.tryEnumerate("some string"));

    assertEquals(COLLISION_1, myEnumerator.valueOf(id1));
    assertEquals(COLLISION_2, myEnumerator.valueOf(id2));
    assertEquals(ContainerUtil.set(COLLISION_1, COLLISION_2), new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  @Test
  public void testUTFString() throws IOException {
    int id1 = myEnumerator.enumerate(UTF_1);
    int id2 = myEnumerator.enumerate(UTF_2);
    assertNotEquals(id1, id2);

    assertEquals(UTF_1, myEnumerator.valueOf(id1));
    assertEquals(UTF_2, myEnumerator.valueOf(id2));
    assertEquals(ContainerUtil.set(UTF_1, UTF_2), new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  @Test
  public void testOpeningClosing() throws IOException {
    ArrayList<String> strings = new ArrayList<>(2000);
    for (int i = 0; i < 2000; ++i) {
      strings.add(createRandomString());
    }
    for (int i = 0; i < 2000; ++i) {
      myEnumerator.enumerate(strings.get(i));
      myEnumerator.close();
      myEnumerator = new TestStringEnumerator(myFile);
    }
    for (int i = 0; i < 2000; ++i) {
      myEnumerator.enumerate(strings.get(i));
      assertFalse(myEnumerator.isDirty());
      myEnumerator.close();
      myEnumerator = new TestStringEnumerator(myFile);
    }
    for (int i = 0; i < 2000; ++i) {
      assertFalse(myEnumerator.isDirty());
      myEnumerator.close();
      myEnumerator = new TestStringEnumerator(myFile);
    }
    HashSet<String> allStringsSet = new HashSet<>(strings);
    assertEquals(allStringsSet, new HashSet<>(myEnumerator.getAllDataObjects(null)));

    String additionalString = createRandomString();
    allStringsSet.add(additionalString);
    myEnumerator.enumerate(additionalString);
    assertTrue(myEnumerator.isDirty());
    assertEquals(allStringsSet, new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  @Test
  public void testValueOfForUnExistedData() throws IOException {
    assertNull(myEnumerator.valueOf(-10));
    assertNull(myEnumerator.valueOf(0));

    assertNull(myEnumerator.valueOf(1));
    assertNull(myEnumerator.valueOf(1000));

    String string = createRandomString();
    int value = myEnumerator.enumerate(string);
    assertNotEquals(1000, value);

    assertNull(myEnumerator.valueOf(1000));
    assertEquals(string, myEnumerator.valueOf(value));

    myEnumerator.force();

    assertNull(myEnumerator.valueOf(1000));
    assertEquals(string, myEnumerator.valueOf(value));
  }

  @Test
  public void testPerformance() throws IOException {
    IntObjectCache<String> stringCache = new IntObjectCache<>(2000);
    IntObjectCache.DeletedPairsListener<String> listener = (key, value) -> {
      try {
        assertEquals(myEnumerator.enumerate(value), key);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    PlatformTestUtil.startPerformanceTest("PersistentStringEnumerator", 1000, () -> {
      stringCache.addDeletedPairsListener(listener);
      for (int i = 0; i < 100000; ++i) {
        String string = createRandomString();
        stringCache.cacheObject(myEnumerator.enumerate(string), string);
      }
      stringCache.removeDeletedPairsListener(listener);
      stringCache.removeAll();
    }).assertTiming();
    myEnumerator.close();
    LOG.debug(String.format("File size = %d bytes\n", myFile.length()));
  }

  @Test
  public void testCorruptionRecovery() throws IOException {
    System.setProperty(PersistentBTreeEnumerator.DO_SELF_HEAL_PROP, Boolean.toString(true));
    try {
      String[] values = new String[] {"AAA", "BBB", "CCC", "DDD", "EEE", "HHH", "JJJ", "ZZZ"};
      int[] ids = new int[values.length];
      for (int i = 0, length = values.length; i < length; i++) {
        String value = values[i];
        ids[i] = myEnumerator.enumerate(value);
      }

      for (int i = 0; i < values.length; i++) {
        String value = values[i];
        assertEquals(ids[i], myEnumerator.catchCorruption(new CorruptAndEnumerateAfter(value)).intValue());
      }
    }
    finally {
      System.setProperty(PersistentBTreeEnumerator.DO_SELF_HEAL_PROP, Boolean.toString(false));
    }
  }

  @Test
  public void testCorruptionRecoveryForLargeEnumerator() throws IOException {
    System.setProperty(PersistentBTreeEnumerator.DO_SELF_HEAL_PROP, Boolean.toString(true));
    try {
      List<String> values = new ArrayList<>();
      IntList ids = new IntArrayList();
      for (int i = 0; i < 1_000_000; i++) {
        String value = String.valueOf(i);
        values.add(value);
        ids.add(myEnumerator.enumerate(value));
      }

      for (int i = 0; i < values.size(); i += 50_000) {
        String value = values.get(i);
        System.out.println("checked " + i);
        assertEquals(ids.getInt(i), myEnumerator.catchCorruption(new CorruptAndEnumerateAfter(value)).intValue());
      }
    }
    finally {
      System.setProperty(PersistentBTreeEnumerator.DO_SELF_HEAL_PROP, Boolean.toString(false));
    }
  }

  private static final StringBuilder builder = new StringBuilder(100);
  private static final Random random = new Random(13101977);

  static String createRandomString() {
    builder.setLength(0);
    int len = random.nextInt(40) + 10;
    for (int i = 0; i < len; ++i) {
      builder.append((char)(32 + random.nextInt(2 + i >> 1)));
    }
    return builder.toString();
  }

  private class CorruptAndEnumerateAfter implements ThrowableComputable<Integer, IOException> {
    private final AtomicBoolean myIoExceptionThrown = new AtomicBoolean(false);
    private final String myValue;

    private CorruptAndEnumerateAfter(String value) { myValue = value; }

    @Override
    public Integer compute() throws IOException {
      if (!myIoExceptionThrown.get()) {
        myIoExceptionThrown.set(true);
        throw new IOException("Corrupted!!!");
      }

      return myEnumerator.tryEnumerate(myValue);
    }
  }
}