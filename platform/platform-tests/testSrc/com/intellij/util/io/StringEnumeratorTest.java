/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.IntObjectCache;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class StringEnumeratorTest extends TestCase {
  private static final String COLLISION_1 = "";
  private static final String COLLISION_2 = "\u0000";
  private static final String UTF_1 = "\ue534";
  private static final String UTF_2 =
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" +
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  private PersistentStringEnumerator myEnumerator;
  private File myFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFile = FileUtil.createTempFile("persistent", "trie");
    myEnumerator = new PersistentStringEnumerator(myFile);
  }

  @Override
  protected void tearDown() throws Exception {
    myEnumerator.close();
    FileUtil.delete(myFile);
    FileUtil.delete(new File(myFile.getParentFile(), myFile.getName() + ".len"));
    assertTrue(!myFile.exists());
    super.tearDown();
  }

  public void testAddEqualStrings() throws IOException {
    final int index = myEnumerator.enumerate("IntelliJ IDEA");
    myEnumerator.enumerate("Just another string");
    assertEquals(index, myEnumerator.enumerate("IntelliJ IDEA"));
  }

  public void testAddEqualStringsAndMuchGarbage() throws IOException {
    final Set<String> stringsAded = new HashSet<String>();
    final int index = myEnumerator.enumerate("IntelliJ IDEA");
    stringsAded.add("IntelliJ IDEA");
    // clear strings and nodes cache
    for (int i = 0; i < 20000; ++i) {
      final String v = Integer.toString(i) + "Just another string";
      stringsAded.add(v);
      final int idx = myEnumerator.enumerate(v);
      assertEquals(v, myEnumerator.valueOf(idx));
    }
    
    assertEquals(index, myEnumerator.enumerate("IntelliJ IDEA"));
    final Set<String> enumerated = new HashSet<String>(myEnumerator.getAllDataObjects(null));
    assertEquals(stringsAded, enumerated);
  }

  public void testCollision() throws Exception {
    int id1 = myEnumerator.enumerate(COLLISION_1);
    int id2 = myEnumerator.enumerate(COLLISION_2);
    assertFalse(id1 == id2);

    assertEquals(COLLISION_1, myEnumerator.valueOf(id1));
    assertEquals(COLLISION_2, myEnumerator.valueOf(id2));
    assertEquals(new HashSet<String>(Arrays.asList(COLLISION_1, COLLISION_2)), new HashSet<String>(myEnumerator.getAllDataObjects(null)));
  }

  public void testCollision1() throws Exception {
    int id1 = myEnumerator.enumerate(COLLISION_1);
    
    assertEquals(id1, myEnumerator.tryEnumerate(COLLISION_1));
    assertEquals(PersistentEnumerator.NULL_ID, myEnumerator.tryEnumerate(COLLISION_2));
    
    int id2 = myEnumerator.enumerate(COLLISION_2);
    assertFalse(id1 == id2);

    assertEquals(id1, myEnumerator.tryEnumerate(COLLISION_1));
    assertEquals(id2, myEnumerator.tryEnumerate(COLLISION_2));
    assertEquals(PersistentEnumerator.NULL_ID, myEnumerator.tryEnumerate("some string"));
    
    assertEquals(COLLISION_1, myEnumerator.valueOf(id1));
    assertEquals(COLLISION_2, myEnumerator.valueOf(id2));
    assertEquals(new HashSet<String>(Arrays.asList(COLLISION_1, COLLISION_2)), new HashSet<String>(myEnumerator.getAllDataObjects(null)));
  }


  public void testUTFString() throws Exception {
    int id1 = myEnumerator.enumerate(UTF_1);
    int id2 = myEnumerator.enumerate(UTF_2);
    assertFalse(id1 == id2);

    assertEquals(UTF_1, myEnumerator.valueOf(id1));
    assertEquals(UTF_2, myEnumerator.valueOf(id2));
    assertEquals(new HashSet<String>(Arrays.asList(UTF_1, UTF_2)), new HashSet<String>(myEnumerator.getAllDataObjects(null)));
  }

  public void testOpeningClosing() throws IOException {
    ArrayList<String> strings = new ArrayList<String>(2000);
    for (int i = 0; i < 2000; ++i) {
      strings.add(createRandomString());
    }
    for (int i = 0; i < 2000; ++i) {
      myEnumerator.enumerate(strings.get(i));
      myEnumerator.close();
      myEnumerator = new PersistentStringEnumerator(myFile);
    }
    for (int i = 0; i < 2000; ++i) {
      myEnumerator.enumerate(strings.get(i));
      assertTrue(!myEnumerator.isDirty());
      myEnumerator.close();
      myEnumerator = new PersistentStringEnumerator(myFile);
    }
    for (int i = 0; i < 2000; ++i) {
      assertTrue(!myEnumerator.isDirty());
      myEnumerator.close();
      myEnumerator = new PersistentStringEnumerator(myFile);
    }
    final HashSet<String> allStringsSet = new HashSet<String>(strings);
    assertEquals(allStringsSet, new HashSet<String>(myEnumerator.getAllDataObjects(null)));

    final String additionalString = createRandomString();
    allStringsSet.add(additionalString);
    myEnumerator.enumerate(additionalString);
    assertTrue(myEnumerator.isDirty());
    assertEquals(allStringsSet, new HashSet<String>(myEnumerator.getAllDataObjects(null)));
  }

  public void testPerformance() throws IOException {
    final IntObjectCache<String> stringCache = new IntObjectCache<String>(2000);
    final IntObjectCache.DeletedPairsListener listener = new IntObjectCache.DeletedPairsListener() {
      @Override
      public void objectRemoved(final int key, final Object value) {
        try {
          assertEquals(myEnumerator.enumerate((String)value), key);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };

    PlatformTestUtil.startPerformanceTest("PersistentStringEnumerator performance failed", 2500, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        stringCache.addDeletedPairsListener(listener);
        for (int i = 0; i < 100000; ++i) {
          final String string = createRandomString();
          stringCache.cacheObject(myEnumerator.enumerate(string), string);
        }
        stringCache.removeDeletedPairsListener(listener);
        stringCache.removeAll();
      }
    }).cpuBound().assertTiming();
    myEnumerator.close();
    System.out.printf("File size = %d bytes\n", myFile.length());
  }

  private static final StringBuilder builder = new StringBuilder(100);
  private static final Random random = new Random();

  static String createRandomString() {
    builder.setLength(0);
    int len = random.nextInt(40) + 10;
    for (int i = 0; i < len; ++i) {
      builder.append((char)(32 + random.nextInt(2 + i >> 1)));
    }
    return builder.toString();
  }
}
