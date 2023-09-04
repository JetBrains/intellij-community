// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class SimpleStringPersistentEnumeratorTest extends StringEnumeratorTestBase<SimpleStringPersistentEnumerator> {

  public SimpleStringPersistentEnumeratorTest() {
    super(/*valuesToTest: */ 1_000);
  }

  @Override
  protected SimpleStringPersistentEnumerator openEnumerator(@NotNull Path storagePath) throws IOException {
    return new SimpleStringPersistentEnumerator(storageFile);
  }
}
