// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.enumerator;

import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableStringEnumerator;
import com.intellij.util.io.StringEnumeratorTestBase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class DurableStringEnumeratorTest extends StringEnumeratorTestBase<DurableStringEnumerator> {

  public DurableStringEnumeratorTest() {
    super(/*valuesToTestOn: */ 500_000);
  }

  @Override
  protected DurableStringEnumerator openEnumeratorImpl(@NotNull Path storagePath) throws IOException {
    return DurableStringEnumerator.open(storagePath);
  }
}