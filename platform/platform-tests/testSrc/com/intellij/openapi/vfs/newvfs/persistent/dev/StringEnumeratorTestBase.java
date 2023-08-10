// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.intellij.util.io.DataEnumeratorEx.NULL_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public abstract class StringEnumeratorTestBase<T extends ScannableDataEnumeratorEx<String>> {

  static {
    IndexDebugProperties.DEBUG = true;
  }

  private static final int ENOUGH_VALUES = 500_000;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected T enumerator;
  protected Path storageFile;
  protected String[] manyValues;

  @Before
  public void setUp() throws Exception {
    storageFile = temporaryFolder.newFile().toPath();
    enumerator = openEnumerator(storageFile);
    manyValues = generateValues(ENOUGH_VALUES, 1, 50);
  }

  @After
  public void tearDown() throws Exception {
    closeEnumerator(enumerator);
  }

  @Test
  public void emptyString_CouldBeEnumerated() throws IOException {
    int emptyStringId = enumerator.enumerate("");
    assertNotEquals(
      "empty string gets normal id (!=NULL_ID)",
      NULL_ID,
      emptyStringId
    );
    assertEquals(
      "Empty string could be get back by its id",
      "",
      enumerator.valueOf(emptyStringId)
    );
  }


  @Test
  public void singleValue_Enumerated_CouldBeGetBackById() throws IOException {
    int id = enumerator.enumerate("A");
    assertEquals(
      "A",
      enumerator.valueOf(id)
    );
  }

  @Test
  public void forSingleValueEnumerated_SecondTimeEnumerate_ReturnsSameId() throws IOException {
    //Check ID is 'stable' at least without interference of other values: not guarantee id
    // stability if other values enumerated in between -- this is NonStrict part is about.
    String value = "A";
    int id1 = enumerator.enumerate(value);
    int id2 = enumerator.enumerate(value);
    assertEquals(
      "[" + value + "] must be given same ID if .enumerate()-ed subsequently",
      id1,
      id2
    );
  }

  @Test
  public void forSingleValueEnumerated_TryEnumerate_ReturnsSameID() throws IOException {
    //Check ID is 'stable' at least without interference of other values: not guarantee id
    // stability if other values enumerated in between -- this is NonStrict part is about.
    String value = "A";
    int id1 = enumerator.enumerate(value);
    int id2 = enumerator.tryEnumerate(value);
    assertEquals(
      "[" + value + "] must be given same ID if .enumerate()/.tryEnumerate()-ed subsequently",
      id1,
      id2
    );
  }


  @Test
  public void manyValuesEnumerated_CouldBeGetBack_ById() throws IOException {
    String[] values = manyValues;
    int[] ids = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      int id = enumerator.enumerate(value);
      ids[i] = id;
    }

    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      String value = values[i];
      assertEquals(
        "value[" + i + "](id: " + id + ") = " + value,
        value,
        enumerator.valueOf(id)
      );
    }
  }

  @Test
  public void forManyValuesEnumerated_IdIsNeverNULL() throws IOException {
    String[] values = manyValues;
    for (String value : values) {
      int id = enumerator.enumerate(value);
      assertNotEquals(
        "id must not be NULL_ID (which is reserved)",
        NULL_ID,
        id
      );
    }
  }

  @Test
  public void forManyValuesEnumerated_SecondTimeEnumerate_ReturnsSameId() throws IOException {
    String[] values = manyValues;
    int[] ids = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      int id = enumerator.enumerate(value);
      ids[i] = id;
    }

    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      String value = values[i];
      assertEquals(
        "[" + i + "]: enumerate(" + value + ") = " + id,
        id,
        enumerator.enumerate(value)
      );
    }
  }

  @Test
  public void forManyValuesEnumerated_TryEnumerate_ReturnsSameId() throws IOException {
    String[] values = manyValues;
    int[] ids = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      int id = enumerator.enumerate(value);
      ids[i] = id;
    }

    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      String value = values[i];
      assertEquals(
        "[" + i + "]: tryEnumerate(" + value + ") = " + id,
        id,
        enumerator.tryEnumerate(value)
      );
    }
  }


  @Test
  public void manyValuesEnumerated_CouldBeGetBack_ById_AfterReload() throws Exception {
    String[] values = manyValues;
    int[] ids = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      int id = enumerator.enumerate(value);
      ids[i] = id;
    }

    closeEnumerator(enumerator);
    enumerator = openEnumerator(storageFile);

    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      String expectedValue = values[i];
      String actualValue = enumerator.valueOf(id);
      assertEquals(
        "value[" + i + "](id: " + id + ") = " + expectedValue,
        expectedValue,
        actualValue
      );
    }
  }

  @Test
  public void manyValuesEnumerated_CouldBeGetBack_ByProcessAllDataObjects() throws Exception {
    String[] values = manyValues;
    for (String value : values) {
      enumerator.enumerate(value);
    }

    Set<String> expectedNames = ContainerUtil.newHashSet(values);
    Set<String> returnedNames = new HashSet<>(expectedNames.size());
    enumerator.processAllDataObjects(name -> {
      returnedNames.add(name);
      return true;
    });

    assertEquals(
      "processAllDataObjects must return all names put into enumerator",
      expectedNames,
      returnedNames
    );
  }

  @Test
  public void manyValuesEnumerated_CouldBeGetBack_ByProcessAllDataObjects_AfterReload() throws Exception {
    String[] values = manyValues;
    for (String value : values) {
      enumerator.enumerate(value);
    }

    closeEnumerator(enumerator);
    enumerator = openEnumerator(storageFile);

    Set<String> expectedNames = ContainerUtil.newHashSet(values);
    Set<String> returnedNames = new HashSet<>(expectedNames.size());
    enumerator.processAllDataObjects(name -> {
      returnedNames.add(name);
      return true;
    });

    assertEquals(
      "processAllDataObjects must return all names put into enumerator",
      expectedNames,
      returnedNames
    );
  }

  @Test
  public void forManyValuesEnumerated_SecondTimeEnumerate_ReturnsSameId_AfterReload() throws Exception {
    String[] values = manyValues;
    int[] ids = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      int id = enumerator.enumerate(value);
      ids[i] = id;
    }

    closeEnumerator(enumerator);
    enumerator = openEnumerator(storageFile);

    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      String value = values[i];
      assertEquals(
        "[" + i + "]: enumerate(" + value + ") = " + id,
        id,
        enumerator.enumerate(value)
      );
    }
  }

  @Test
  public void forManyValuesEnumerated_TryEnumerate_ReturnsSameId_AfterReload() throws Exception {
    String[] values = manyValues;
    int[] ids = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      int id = enumerator.enumerate(value);
      ids[i] = id;
    }

    closeEnumerator(enumerator);
    enumerator = openEnumerator(storageFile);

    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      String value = values[i];
      assertEquals(
        "[" + i + "]: tryEnumerate(" + value + ") = " + id,
        id,
        enumerator.tryEnumerate(value)
      );
    }
  }


  @Test
  @Ignore("poor-man benchmark, not a test")
  public void manyValuesEnumerated_CouldBeGetBack_ByProcessAllDataObjects_AfterReload_Benchmark() throws Exception {
    String[] values = manyValues;
    for (String value : values) {
      enumerator.enumerate(value);
    }

    closeEnumerator(enumerator);
    enumerator = openEnumerator(storageFile);

    Set<String> expectedNames = ContainerUtil.newHashSet(values);
    Set<String> returnedNames = new HashSet<>(expectedNames.size());
    enumerator.processAllDataObjects(name -> {
      returnedNames.add(name);
      return true;
    });

    long startedAtNs = System.nanoTime();
    for (int i = 0; i < 16; i++) {
      enumerator.processAllDataObjects(name -> {
        returnedNames.add(name);
        return true;
      });
    }
    System.out.println(returnedNames.size() + " names: " + TimeoutUtil.getDurationMillis(startedAtNs) / 10.0 + " ms per listing");

    assertEquals(
      "processAllDataObjects must return all names put into enumerator",
      expectedNames,
      returnedNames
    );
  }


  protected void closeEnumerator(DataEnumerator<String> enumerator) throws Exception {
    if (enumerator instanceof AutoCloseable) {
      ((AutoCloseable)enumerator).close();
    }
  }

  protected abstract T openEnumerator(@NotNull Path storagePath) throws IOException;

  protected static String @NotNull [] generateValues(int poolSize, int minStringSize, int maxStringSize) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return Stream.generate(() -> {
        int length = rnd.nextInt(minStringSize, maxStringSize);
        char[] chars = new char[length];
        for (int i = 0; i < chars.length; i++) {
          chars[i] = Character.forDigit(rnd.nextInt(0, 36), 36);
        }
        return new String(chars);
      })
      .limit(poolSize)
      .toArray(String[]::new);
  }
}