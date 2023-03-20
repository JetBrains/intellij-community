/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.IntObjectCache;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class StringEnumeratorTest extends TestCase {
  private static final String COLLISION_1 = "";
  private static final String COLLISION_2 = "\u0000";

  private PersistentStringEnumerator myEnumerator;
  private File myFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFile = FileUtil.createTempFile("persistent", "trie");
    myEnumerator = new PersistentStringEnumerator(myFile.toPath());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myEnumerator.close();
      myEnumerator = null;
      IOUtil.deleteAllFilesStartingWith(myFile);
      assertTrue(!myFile.exists());
    }
    finally {
      super.tearDown();
    }
  }

  public void testAddEqualStrings() throws IOException {
    final int index = myEnumerator.enumerate("IntelliJ IDEA");
    myEnumerator.enumerate("Just another string");
    assertEquals(index, myEnumerator.enumerate("IntelliJ IDEA"));
  }

  public void testAddEqualStringsAndMuchGarbage() throws IOException {
    final Set<String> stringsAded = new HashSet<>();
    final int index = myEnumerator.enumerate("IntelliJ IDEA");
    stringsAded.add("IntelliJ IDEA");
    // clear strings and nodes cache
    for (int i = 0; i < 20000; ++i) {
      final String v = i + "Just another string";
      stringsAded.add(v);
      final int idx = myEnumerator.enumerate(v);
      assertEquals(v, myEnumerator.valueOf(idx));
    }
    
    assertEquals(index, myEnumerator.enumerate("IntelliJ IDEA"));
    final Set<String> enumerated = new HashSet<>(myEnumerator.getAllDataObjects(null));
    assertEquals(stringsAded, enumerated);
  }

  public void testCollision() throws Exception {
    int id1 = myEnumerator.enumerate(COLLISION_1);
    int id2 = myEnumerator.enumerate(COLLISION_2);
    assertFalse(id1 == id2);

    assertEquals(COLLISION_1, myEnumerator.valueOf(id1));
    assertEquals(COLLISION_2, myEnumerator.valueOf(id2));
    assertEquals(Set.of(COLLISION_1, COLLISION_2),
                 new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  public void testCollision1() throws Exception {
    int id1 = myEnumerator.enumerate(COLLISION_1);
    
    assertEquals(id1, myEnumerator.tryEnumerate(COLLISION_1));
    assertEquals(PersistentEnumeratorBase.NULL_ID, myEnumerator.tryEnumerate(COLLISION_2));
    
    int id2 = myEnumerator.enumerate(COLLISION_2);
    assertFalse(id1 == id2);

    assertEquals(id1, myEnumerator.tryEnumerate(COLLISION_1));
    assertEquals(id2, myEnumerator.tryEnumerate(COLLISION_2));
    assertEquals(PersistentEnumeratorBase.NULL_ID, myEnumerator.tryEnumerate("some string"));
    
    assertEquals(COLLISION_1, myEnumerator.valueOf(id1));
    assertEquals(COLLISION_2, myEnumerator.valueOf(id2));
    assertEquals(Set.of(COLLISION_1, COLLISION_2), new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }


  public void testUTFString() throws Exception {
    final String UTF_1 = "\ue534";
    final String UTF_2 = StringUtil.repeatSymbol('a', 624);
    int id1 = myEnumerator.enumerate(UTF_1);
    int id2 = myEnumerator.enumerate(UTF_2);
    assertFalse(id1 == id2);

    assertEquals(UTF_1, myEnumerator.valueOf(id1));
    assertEquals(UTF_2, myEnumerator.valueOf(id2));
    assertEquals(Set.of(UTF_1, UTF_2), new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  public void testOpeningClosing() throws IOException {
    ArrayList<String> strings = new ArrayList<>(2000);
    for (int i = 0; i < 2000; ++i) {
      strings.add(createRandomString());
    }
    for (int i = 0; i < 2000; ++i) {
      myEnumerator.enumerate(strings.get(i));
      myEnumerator.close();
      myEnumerator = new PersistentStringEnumerator(myFile.toPath());
    }
    for (int i = 0; i < 2000; ++i) {
      myEnumerator.enumerate(strings.get(i));
      assertTrue(!myEnumerator.isDirty());
      myEnumerator.close();
      myEnumerator = new PersistentStringEnumerator(myFile.toPath());
    }
    for (int i = 0; i < 2000; ++i) {
      assertTrue(!myEnumerator.isDirty());
      myEnumerator.close();
      myEnumerator = new PersistentStringEnumerator(myFile.toPath());
    }
    final HashSet<String> allStringsSet = new HashSet<>(strings);
    assertEquals(allStringsSet, new HashSet<>(myEnumerator.getAllDataObjects(null)));

    final String additionalString = createRandomString();
    allStringsSet.add(additionalString);
    myEnumerator.enumerate(additionalString);
    assertTrue(myEnumerator.isDirty());
    assertEquals(allStringsSet, new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  public void testPerformance() throws IOException {
    final IntObjectCache<String> stringCache = new IntObjectCache<>(2000);
    final IntObjectCache.DeletedPairsListener<String> listener = (key, value) -> {
      try {
        assertEquals(myEnumerator.enumerate(value), key);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    PlatformTestUtil.startPerformanceTest("PersistentStringEnumerator.enumerate", 700, () -> {
      stringCache.addDeletedPairsListener(listener);
      for (int i = 0; i < 100000; ++i) {
        final String string = createRandomString();
        stringCache.cacheObject(myEnumerator.enumerate(string), string);
      }
      stringCache.removeDeletedPairsListener(listener);
      stringCache.removeAll();
    }).attempts(1).assertTiming();
    myEnumerator.close();
    System.out.printf("File size = %d bytes\n", myFile.length());
  }

  private static final StringBuilder builder = new StringBuilder(100);
  private static final Random random = new Random(2_71828);

  public static String createRandomString() {
    return createRandomString(random);
  }

  @NotNull
  static String createRandomString(Random random) {
    builder.setLength(0);
    int len = random.nextInt(40) + 10;
    for (int i = 0; i < len; ++i) {
      builder.append((char)(32 + random.nextInt(2 + i >> 1)));
    }
    return builder.toString();
  }
}
