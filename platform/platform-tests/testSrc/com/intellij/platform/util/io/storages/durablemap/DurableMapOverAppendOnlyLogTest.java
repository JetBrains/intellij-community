// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;

public class DurableMapOverAppendOnlyLogTest extends DurableMapTestBase<String, String, DurableMapOverAppendOnlyLog<String, String>> {

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

}
