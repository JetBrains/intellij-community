// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.KeyValueStoreScenarios.StringKeyScenarios;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.BasicScenarios;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.CompactionScenarios;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.LookupCorruptionRecoveryScenarios;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.LookupLossRecoveryScenarios;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static com.intellij.platform.util.io.storages.KeyValueTestData.STRING_SUBSTRATE_DECODER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DurableMapOverAppendOnlyLogTest
  extends DurableMapTestBase<String, String, DurableMapOverAppendOnlyLog<String, String>>
  implements BasicScenarios<String, String, DurableMapOverAppendOnlyLog<String, String>>,
             StringKeyScenarios<String, DurableMapOverAppendOnlyLog<String, String>>,
             CompactionScenarios<String, String, DurableMapOverAppendOnlyLog<String, String>>,
             LookupLossRecoveryScenarios<String, String, DurableMapOverAppendOnlyLog<String, String>>,
             LookupCorruptionRecoveryScenarios<String, String, DurableMapOverAppendOnlyLog<String, String>> {

  public DurableMapOverAppendOnlyLogTest() {
    super(STRING_SUBSTRATE_DECODER);
  }

  @Override
  protected @NotNull StorageFactory<DurableMapOverAppendOnlyLog<String, String>> factory() {
    KeyDescriptorEx<String> stringAsUTF8 = stringAsUTF8();
    //TODO RC: test both cases: with and without valueEquality
    return DurableMapFactory.withDefaults(
      stringAsUTF8,
      (DataExternalizerEx<String>)stringAsUTF8
    );
  }

  @Override
  protected boolean isAppendOnly() {
    return true;
  }

  @Test
  public void containsMappingChecksTheKeyWhenHashesCollide() throws IOException {
    assertEquals("FB".hashCode(), "Ea".hashCode());
    storage.put("FB", "value");

    assertFalse(storage.containsMapping("Ea"));
  }

}
