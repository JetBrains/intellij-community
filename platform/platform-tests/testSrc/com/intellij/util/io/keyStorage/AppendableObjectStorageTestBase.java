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

  public static final int ENOUGH_VALUES = 128 << 10;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected AppendableObjectStorage<V> appendableStorage;


  // ================= simple single-value tests: =====================================================

  @Test
  public void valueAppendedToStorage_ReadBackAsIs() throws Exception {
    final V valueAppended = generateValue();

    appendableStorage.lockWrite();
    try {
      final int valueId = appendableStorage.append(valueAppended);
      final V valueReadBack = appendableStorage.read(valueId, false);
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
      final int valueId = appendableStorage.append(valueWritten);
      assertThat(
        "Value just appended must have same bytes",
        appendableStorage.checkBytesAreTheSame(valueId, valueWritten),
        is(true)
      );
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void valueAppendedToStorage_MustNotBeEqualToMutatedVersionOfItself() throws Exception {
    final V valueWritten = generateValue();
    appendableStorage.lockWrite();
    try {
      final int valueId = appendableStorage.append(valueWritten);
      assertThat(
        "Value just appended must NOT have same bytes as mutated version",
        appendableStorage.checkBytesAreTheSame(valueId, mutateValue(valueWritten)),
        is(false)
      );
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void valueAppendedToStorage_ReadBackAsIs_WithProcessAll() throws Exception {
    V valueAppended = generateValue();


    final int valueIdAppended;
    appendableStorage.lockWrite();
    try {
      valueIdAppended = appendableStorage.append(valueAppended);
    }
    finally {
      appendableStorage.unlockWrite();
    }
    List<Pair<Integer, V>> valuesAndValueIds = new ArrayList<>();
    appendableStorage.processAll((valueId, value) -> {
      valuesAndValueIds.add(Pair.pair(valueId, value));
      return true;
    });

    assertThat(
      "Value appended must be read back as-is by .processAll()",
      valuesAndValueIds,
      contains(Pair.pair(valueIdAppended, valueAppended))
    );
  }

  // ================= multi-value property-based tests: ===========================================

  @Test
  public void manyValuesAppendedToStorage_AllReadBackAsIs() throws Exception {
    List<V> valuesAppended = generateValues(ENOUGH_VALUES);

    List<Pair<Integer, V>> valuesAndvalueIds = new ArrayList<>();
    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        int valueId = appendableStorage.append(valueToAppend);
        valuesAndvalueIds.add(Pair.pair(valueId, valueToAppend));
      }
      for (Pair<Integer, V> valuesAndvalueId : valuesAndvalueIds) {
        final V valueAppended = valuesAndvalueId.second;
        final int valueId = valuesAndvalueId.first;
        V valueReadBack = appendableStorage.read(valueId, false);
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

    List<Pair<Integer, V>> valuesAndValueIds = new ArrayList<>();
    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        int valueId = appendableStorage.append(valueToAppend);
        valuesAndValueIds.add(Pair.pair(valueId, valueToAppend));
      }
      for (Pair<Integer, V> valuesAndValueId : valuesAndValueIds) {
        final int valueId = valuesAndValueId.first;
        final V valueAppended = valuesAndValueId.second;
        assertThat(
          "Value appended [" + valueAppended + "] at [valueId: " + valueId + "] must have same bytes",
          appendableStorage.checkBytesAreTheSame(valueId, valueAppended),
          is(true)
        );
      }
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void manyValuesAppendedToStorage_AreNotEqualToMutatedVersionsOfThemself() throws Exception {
    List<V> valuesAppended = generateValues(ENOUGH_VALUES);

    List<Pair<Integer, V>> valuesAndValueIds = new ArrayList<>();
    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        int valueId = appendableStorage.append(valueToAppend);
        valuesAndValueIds.add(Pair.pair(valueId, valueToAppend));
      }
      for (Pair<Integer, V> valuesAndValueId : valuesAndValueIds) {
        final int valueId = valuesAndValueId.first;
        final V valueAppended = mutateValue(valuesAndValueId.second);
        assertThat(
          "Value appended [" + valueAppended + "] at [valueId: " + valueId + "] must NOT have same bytes",
          appendableStorage.checkBytesAreTheSame(valueId, valueAppended),
          is(false)
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

    List<Pair<Integer, V>> valuesAndValueIdsAppended = new ArrayList<>();
    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        int valueId = appendableStorage.append(valueToAppend);
        valuesAndValueIdsAppended.add(Pair.pair(valueId, valueToAppend));
      }

      //RC: must flush data before .processAll()!
      appendableStorage.force();

      List<Pair<Integer, V>> valuesAndValueIdsReadBack = new ArrayList<>();
      appendableStorage.processAll((valueId, value) -> {
        valuesAndValueIdsReadBack.add(Pair.pair(valueId, value));
        return true;
      });

      assertThat(
        "Values appended must be all read as-is (with apt valueIds) by .processAll()",
        valuesAndValueIdsReadBack,
        containsInAnyOrder(valuesAndValueIdsAppended.toArray())
      );
    }
    finally {
      appendableStorage.unlockWrite();
    }
  }

  @Test
  public void storage_becomeNotDirty_AfterFlush() throws Exception {
    List<V> valuesAppended = generateValues(ENOUGH_VALUES);

    appendableStorage.lockWrite();
    try {
      for (V valueToAppend : valuesAppended) {
        appendableStorage.append(valueToAppend);
        //It seems natural to check appendableStorage.isDirty here, but it is not guaranteed to be dirty,
        // because underlying PagedStorage/FilePageCache could flush the pages just because they need
        // room for the new pages to cache. Hence appendableStorage.isDirty in 99+% of cases, but sometimes
        // it is !dirty even though something was just appended to it.

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

  /** introduce some (random?) modification so returned value !equals value passed in */
  protected abstract V mutateValue(@NotNull V value);

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
