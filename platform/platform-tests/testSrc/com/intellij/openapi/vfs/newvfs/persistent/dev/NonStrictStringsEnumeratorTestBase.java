// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.DataEnumerator;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public abstract class NonStrictStringsEnumeratorTestBase<T extends DataEnumerator<String>> {

  static{
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
    manyValues = generateValues(ENOUGH_VALUES);
  }

  @After
  public void tearDown() throws Exception {
    closeEnumerator(enumerator);
  }

  @Test
  public void singleValueEnumeratedCouldBeGetBackById() throws IOException {
    final int id = enumerator.enumerate("A");
    assertEquals(
      "A",
      enumerator.valueOf(id)
    );
  }

  @Test
  public void singleValueEnumeratedToSameIDIfCalledImmediately() throws IOException {
    //Check ID is 'stable' at least without interference of other values: not guarantee id
    // stability if other values enumerated in between -- this is NonStrict part is about.
    final String value = "A";
    final int id1 = enumerator.enumerate(value);
    final int id2 = enumerator.enumerate(value);
    assertEquals(
      "["+value+"] must be given same ID if .enumerate()-ed subsequently",
      id1,
      id2
    );
  }

  @Test
  public void manyValuesEnumeratedCouldBeGetBackById() throws IOException {
    final String[] values = manyValues;
    final int[] ids = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      final String value = values[i];
      final int id = enumerator.enumerate(value);
      ids[i] = id;
    }

    for (int i = 0; i < ids.length; i++) {
      final int id = ids[i];
      final String value = values[i];
      assertEquals(
        "value[" + i + "](id: " + id + ") = " + value,
        value,
        enumerator.valueOf(id)
      );
    }
  }

  @Test
  public void manyValuesEnumeratedCouldBeGetBackByIdAfterReload() throws Exception {
    final String[] values = manyValues;
    final int[] ids = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      final String value = values[i];
      final int id = enumerator.enumerate(value);
      ids[i] = id;
    }
    closeEnumerator(enumerator);

    enumerator = openEnumerator(storageFile);

    for (int i = 0; i < ids.length; i++) {
      final int id = ids[i];
      final String expectedValue = values[i];
      final String actualValue = enumerator.valueOf(id);
      assertEquals(
        "value[" + i + "](id: " + id + ") = " + expectedValue,
        expectedValue,
        actualValue
      );
    }
  }

  protected void closeEnumerator(final DataEnumerator<String> enumerator) throws Exception {
    if (enumerator instanceof AutoCloseable) {
      ((AutoCloseable)enumerator).close();
    }
  }

  protected abstract T openEnumerator(final @NotNull Path storagePath) throws IOException;

  private static String[] generateValues(final int size) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return Stream.generate(() -> {
        final int length = rnd.nextInt(1, 50);
        final char[] chars = new char[length];
        for (int i = 0; i < chars.length; i++) {
          chars[i] = Character.forDigit(rnd.nextInt(0, 36), 36);
        }
        return new String(chars);
      })
      .limit(size)
      .toArray(String[]::new);
  }
}