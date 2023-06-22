// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.keyStorage;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class AppendableObjectStorageTestBase<V> {

  public static final int ENOUGH_VALUES = 32 << 10;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private AppendableObjectStorage<V> appendableStorage;


  // ================= simple single-value tests: =====================================================

  @Test
  public void valueAppendedToStorage_ReadBackAsIs() throws Exception {
    final V valueAppended = generateValue();

    appendableStorage.lockWrite();
    try {
      final int offset = appendableStorage.append(valueAppended);
      final V valueReadBack = appendableStorage.read(offset, false);
      assertThat(
        "Value appended must be read back as-is",
        valueReadBack,
        equalTo(valueAppended)
      );
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void valueAppendedToStorage_MustBeEqualToItself() throws Exception {
    final V valueWritten = generateValue();
    appendableStorage.lockWrite();
    try {
      final int offset = appendableStorage.append(valueWritten);
      assertThat(
        "Value just appended must have same bytes",
        appendableStorage.checkBytesAreTheSame(offset, valueWritten),
        is(true)
      );
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void valueAppendedToStorage_ReadBackAsIs_WithProcessAll() throws Exception {
    V valueAppended = generateValue();


    appendableStorage.lockWrite();
    try {
      int offsetAppended = appendableStorage.append(valueAppended);

      //RC: must flush data before .processAll()!
      appendableStorage.force();

      List<Pair<Integer, V>> valuesAndOffsets = new ArrayList<>();
      appendableStorage.processAll((valueOffset, value) -> {
        valuesAndOffsets.add(Pair.pair(valueOffset, value));
        return true;
      });

      assertThat(
        "Value appended must be read back as-is by .processAll()",
        valuesAndOffsets,
        contains(Pair.pair(offsetAppended, valueAppended))
      );
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  // ================= multi-value property-based tests: ===========================================

  @Test
  public void manyValuesAppendedToStorage_AllReadBackAsIs() throws Exception {
    List<V> valuesAppended = generateValues(ENOUGH_VALUES);

    List<Pair<Integer, V>> valuesAndOffsets = new ArrayList<>();
    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        int appendedAtOffset = appendableStorage.append(valueToAppend);
        valuesAndOffsets.add(Pair.pair(appendedAtOffset, valueToAppend));
      }
      for (Pair<Integer, V> valuesAndOffset : valuesAndOffsets) {
        final V valueAppended = valuesAndOffset.second;
        final Integer appendedAtOffset = valuesAndOffset.first;
        V valueReadBack = appendableStorage.read(appendedAtOffset, false);
        assertThat(
          "Value appended must be read back as-is",
          valueReadBack,
          equalTo(valueAppended)
        );
      }
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void manyValuesAppendedToStorage_MustBeAllEqualToThemself() throws Exception {
    List<V> valuesAppended = generateValues(ENOUGH_VALUES);

    List<Pair<Integer, V>> valuesAndOffsets = new ArrayList<>();
    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        int appendedAtOffset = appendableStorage.append(valueToAppend);
        valuesAndOffsets.add(Pair.pair(appendedAtOffset, valueToAppend));
      }
      for (Pair<Integer, V> valuesAndOffset : valuesAndOffsets) {
        final V valueAppended = valuesAndOffset.second;
        final Integer appendedAtOffset = valuesAndOffset.first;
        assertThat(
          "Value appended ([" + valueAppended + "] at offset " + appendableStorage + ") must have same bytes",
          appendableStorage.checkBytesAreTheSame(appendedAtOffset, valueAppended),
          is(true)
        );
      }
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void manyValuesAppendedToStorage_AllReadBackAsIs_WithProcessAll() throws Exception {
    List<V> valuesAppended = generateValues(ENOUGH_VALUES);

    List<Pair<Integer, V>> valuesAndOffsetsAppended = new ArrayList<>();
    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        int appendedAtOffset = appendableStorage.append(valueToAppend);
        valuesAndOffsetsAppended.add(Pair.pair(appendedAtOffset, valueToAppend));
      }

      //RC: must flush data before .processAll()!
      appendableStorage.force();

      List<Pair<Integer, V>> valuesAndOffsetsReadBack = new ArrayList<>();
      appendableStorage.processAll((valueOffset, value) -> {
        valuesAndOffsetsReadBack.add(Pair.pair(valueOffset, value));
        return true;
      });

      assertThat(
        "Values appended must be all read as-is (with apt offsets) by .processAll()",
        valuesAndOffsetsReadBack,
        containsInAnyOrder(valuesAndOffsetsAppended.toArray())
      );
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void storage_isDirty_afterEachAppend_AndBecomeNotDirtyAfterFlush() throws Exception {
    List<V> valuesAppended = generateValues(ENOUGH_VALUES);

    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        appendableStorage.append(valueToAppend);
        assertThat(
          "Storage must be .dirty since value was just appended to it",
          appendableStorage.isDirty(),
          is(true)
        );

        appendableStorage.force();

        assertThat(
          "Storage must NOT be .dirty since it was just flushed",
          appendableStorage.isDirty(),
          is(false)
        );
      }
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }


  // ============== infrastructure: ===============================================================

  protected abstract @NotNull AppendableObjectStorage<V> createStorage(Path path) throws IOException;

  protected abstract @NotNull V generateValue();

  @Before
  public void setUp() throws Exception {
    appendableStorage = createStorage(temporaryFolder.newFile().toPath());
  }

  @After
  public void tearDown() throws Exception {
    if (appendableStorage != null) {
      appendableStorage.lockWrite();
      try {
        appendableStorage.close();
      }
      finally {
        appendableStorage.lockWrite();
      }
    }
  }

  private List<V> generateValues(int valuesCount) {
    final ArrayList<V> values = new ArrayList<>();
    for (int i = 0; i < valuesCount; i++) {
      values.add(generateValue());
    }
    return values;
  }
}
