// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.IntRef;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.intellij.util.io.DataEnumeratorEx.NULL_ID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public abstract class StringEnumeratorTestBase<T extends ScannableDataEnumeratorEx<String>> {

  static {
    IndexDebugProperties.DEBUG = true;
  }

  private final int valuesCountToTest;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected T enumerator;
  protected Path storageFile;
  protected String[] manyValues;

  private final List<T> enumeratorsOpened = new ArrayList<>();

  protected StringEnumeratorTestBase(int valuesToTest) {
    valuesCountToTest = valuesToTest;
  }

  @Before
  public void setUp() throws Exception {
    storageFile = temporaryFolder.newFile().toPath();
    enumerator = openEnumerator(storageFile);
    manyValues = generateUniqueValues(valuesCountToTest, 1, 50);
  }

  @After
  public void tearDown() throws Exception {
    //RC: it is important to first unmap _all_, and then try to clean (delete) _all_, because it could be
    //    >1 enumerators opened over same file, hence >1 mapped buffers, and on Windows one can't delete
    //    the file until at least 1 mapped region not yet unmapped, so attempt to delete the file after
    //    unmapping buffers of 1st enumerator will fail because same file is mapped in the 2nd enumerator:
    for (T enumeratorOpened : enumeratorsOpened) {
      closeEnumerator(enumeratorOpened);
    }
    for (T enumeratorOpened : enumeratorsOpened) {
      if (enumeratorOpened instanceof CleanableStorage) {
        ((CleanableStorage)enumeratorOpened).closeAndClean();
      }
    }
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
  public void nullValue_EnumeratedTo_NULL_ID() throws IOException {
    int id = enumerator.enumerate(null);
    assertEquals(
      "null value enumerated to NULL_ID",
      NULL_ID,
      id
    );
  }


  @Test
  public void valueOf_NULL_ID_IsNull() throws IOException {
    String value = enumerator.valueOf(NULL_ID);
    assertNull(
      "valueOf(NULL_ID(=0)) must be null",
      value
    );
  }


  @Test
  public void singleValue_Enumerated_CouldBeGetBackById() throws IOException {
    int id = enumerator.enumerate("A");
    assertEquals(
      "A",
      enumerator.valueOf(id)
    );
    assertEquals(
      "Only 1 object was enumerated",
      1,
      enumerator.recordsCount()
    );
  }

  @Test
  public void forSingleValueEnumerated_SecondTimeEnumerate_ReturnsSameId() throws IOException {
    //Check ID is 'stable' at least without an interference of other values: not guarantee id
    // stability if other values enumerated in between -- this is NonStrict part is about.
    String value = "A";
    int id1 = enumerator.enumerate(value);
    int id2 = enumerator.enumerate(value);
    assertEquals(
      "[" + value + "] must be given same ID if .enumerate()-ed subsequently",
      id1,
      id2
    );
    assertEquals(
      "Only 1 object was enumerated",
      1,
      enumerator.recordsCount()
    );
  }

  @Test
  public void forSingleValueEnumerated_TryEnumerate_ReturnsSameID() throws IOException {
    //Check ID is 'stable' at least without an interference of other values: not guarantee id
    // stability if other values enumerated in between -- this is NonStrict part is about.
    String value = "A";
    int id1 = enumerator.enumerate(value);
    int id2 = enumerator.tryEnumerate(value);
    assertEquals(
      "[" + value + "] must be given same ID if .enumerate()/.tryEnumerate()-ed subsequently",
      id1,
      id2
    );
    assertEquals(
      "Only 1 object was enumerated",
      1,
      enumerator.recordsCount()
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
      assertEquals(
        (i + 1) + " unique objects were enumerated",
        i + 1,
        enumerator.recordsCount()
      );
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
    assertEquals(
      values.length + " unique objects were enumerated",
      values.length,
      enumerator.recordsCount()
    );

    for (int i = 0; i < ids.length; i++) {
      int id = ids[i];
      String value = values[i];
      assertEquals(
        "[" + i + "]: enumerate(" + value + ") = " + id,
        id,
        enumerator.enumerate(value)
      );
    }
    assertEquals(
      values.length + " unique objects were enumerated",
      values.length,
      enumerator.recordsCount()
    );
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
  public void forManyValuesEnumerated_ImmediateTryEnumerate_ReturnsSameId() throws IOException {
    String[] values = manyValues;
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      int id = enumerator.enumerate(value);
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

    assertEquals(
      values.length + " unique objects were enumerated before reload",
      values.length,
      enumerator.recordsCount()
    );
  }

  @Test
  public void manyValuesEnumerated_CouldBeGetBack_ByForEach() throws Exception {
    String[] values = manyValues;
    for (String value : values) {
      enumerator.enumerate(value);
    }

    Set<String> expectedNames = ContainerUtil.newHashSet(values);
    Set<String> returnedNames = new HashSet<>(expectedNames.size());
    enumerator.forEach((nameId, name) -> {
      returnedNames.add(name);
      return true;
    });

    assertEquals(
      ".forEach() must return all names put into enumerator",
      expectedNames,
      returnedNames
    );
  }

  @Test
  public void forManyValuesEnumerated_TheirIdsReported_ByForEach() throws Exception {
    String[] values = manyValues;
    IntSet expectedIdsSet = new IntOpenHashSet();
    for (String value : values) {
      expectedIdsSet.add(enumerator.enumerate(value));
    }

    IntSet returnedIdsSet = new IntOpenHashSet(expectedIdsSet.size());
    enumerator.forEach((nameId, name) -> {
      returnedIdsSet.add(nameId);
      return true;
    });

    int[] expectedIds = expectedIdsSet.toIntArray();
    int[] returnedIds = returnedIdsSet.toIntArray();
    Arrays.sort(expectedIds);
    Arrays.sort(returnedIds);

    assertArrayEquals(
      ".forEach() must return all ids returned by .enumerate() before",
      expectedIds,
      returnedIds
    );
  }


  @Test
  public void manyValuesEnumerated_CouldBeGetBack_ByForEach_AfterReload() throws Exception {
    String[] values = manyValues;
    for (String value : values) {
      enumerator.enumerate(value);
    }

    closeEnumerator(enumerator);
    enumerator = openEnumerator(storageFile);

    Set<String> expectedNames = ContainerUtil.newHashSet(values);
    Set<String> returnedNames = new HashSet<>(expectedNames.size());
    enumerator.forEach((nameId, name) -> {
      returnedNames.add(name);
      return true;
    });

    assertEquals(
      ".forEach() must return all names put into enumerator",
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
  public void runningMultiThreaded_valuesListedByForEach_alwaysKnownToTryEnumerate() throws Exception {
    //not too many threads, 'cos we want to check .forEach values in the middle as many times as possible, and
    // with many threads we fill the enumerator up too fast.
    int enumeratingThreadsCount = 2;

    int valuesPerThread = manyValues.length / enumeratingThreadsCount;
    int actualValuesToEnumerate = enumeratingThreadsCount * valuesPerThread;

    ExecutorService pool = Executors.newFixedThreadPool(enumeratingThreadsCount);
    try {
      Callable<Void>[] enumeratingTasks = new Callable[enumeratingThreadsCount];
      for (int threadNo = 0; threadNo < enumeratingThreadsCount; threadNo++) {
        int finalThreadNo = threadNo;
        enumeratingTasks[threadNo] = () -> {
          for (int i = 0; i < valuesPerThread; i++) {
            String value = manyValues[i * enumeratingThreadsCount + finalThreadNo];
            enumerator.enumerate(value);
          }
          return null;
        };
      }
      for (Callable<Void> task : enumeratingTasks) {
        pool.submit(task);
      }

      pool.shutdown();

      int cycles = 0;
      IntArrayList valuesCheckedByTurn = new IntArrayList();
      do {
        int valuesChecked = checkAllForEachValuesExist(enumerator);
        valuesCheckedByTurn.add(valuesChecked);
        cycles++;
      }
      while (!pool.isTerminated());

      int valuesChecked = checkAllForEachValuesExist(enumerator);
      System.out.println("cycles: " + cycles + ", values checked by turn: " + valuesCheckedByTurn
                         + "\nvalues finally: " + valuesChecked + "/" + actualValuesToEnumerate);
    }
    finally {
      pool.shutdown();
      pool.awaitTermination(100, SECONDS);
    }
  }


  @Test
  @Ignore("poor-man benchmark, not a test")
  public void manyValuesEnumerated_CouldBeGetBack_ByForEach_AfterReload_Benchmark() throws Exception {
    String[] values = manyValues;
    for (String value : values) {
      enumerator.enumerate(value);
    }

    closeEnumerator(enumerator);
    enumerator = openEnumerator(storageFile);

    Set<String> expectedNames = ContainerUtil.newHashSet(values);
    Set<String> returnedNames = new HashSet<>(expectedNames.size());
    enumerator.forEach((nameId, name) -> {
      returnedNames.add(name);
      return true;
    });

    long startedAtNs = System.nanoTime();
    for (int i = 0; i < 16; i++) {
      enumerator.forEach((nameId, name) -> {
        returnedNames.add(name);
        return true;
      });
    }
    System.out.println(returnedNames.size() + " names: " + TimeoutUtil.getDurationMillis(startedAtNs) / 10.0 + " ms per listing");

    assertEquals(
      ".forEach() must return all names put into enumerator",
      expectedNames,
      returnedNames
    );
  }

  @Test
  public void ifEnumeratorIsAutoCloseable_CloseIsSafeToCallTwice() throws Exception {
    assumeTrue(enumerator instanceof AutoCloseable);

    ((AutoCloseable)enumerator).close();
    ((AutoCloseable)enumerator).close();
  }


  protected void closeEnumerator(DataEnumerator<String> enumerator) throws Exception {
    if (enumerator instanceof Unmappable) {
      ((Unmappable)enumerator).closeAndUnsafelyUnmap();
    }
    else if (enumerator instanceof AutoCloseable) {
      ((AutoCloseable)enumerator).close();
    }
  }

  protected final T openEnumerator(@NotNull Path storagePath) throws IOException {
    T enumerator = openEnumeratorImpl(storagePath);
    enumeratorsOpened.add(enumerator);
    return enumerator;
  }

  protected abstract T openEnumeratorImpl(@NotNull Path storagePath) throws IOException;

  protected static String @NotNull [] generateUniqueValues(int poolSize, int minStringSize, int maxStringSize) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    //Random rnd = new Random(1);//for debugging
    return Stream.generate(() -> {
        int length = rnd.nextInt(minStringSize, maxStringSize);
        char[] chars = new char[length];
        for (int i = 0; i < chars.length; i++) {
          chars[i] = Character.forDigit(rnd.nextInt(0, 36), 36);
        }
        return new String(chars);
      })
      .distinct()
      .limit(poolSize)
      .toArray(String[]::new);
  }

  protected int checkAllForEachValuesExist(@NotNull ScannableDataEnumeratorEx<String> enumerator) throws IOException {
    IntRef valuesChecked = new IntRef(0);
    enumerator.forEach((id, value) -> {
      valuesChecked.inc();
      try {
        int valueId = enumerator.tryEnumerate(value);
        if (valueId == NULL_ID) {
          throw new AssertionError("value[" + value + "] enumerated to NULL");
        }
        if (valueId != id) {
          throw new AssertionError("value[" + value + "] enumerated to " + valueId + " while must be " + id);
        }
        return true;
      }
      catch (Throwable t) {
        throw new AssertionError("name[" + value + "] failed to enumerate", t);
      }
    });
    return valuesChecked.get();
  }
}