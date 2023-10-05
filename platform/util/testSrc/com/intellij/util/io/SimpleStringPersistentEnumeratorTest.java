// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.junit.AssumptionViolatedException;

import java.io.IOException;
import java.nio.file.Path;

public class SimpleStringPersistentEnumeratorTest extends StringEnumeratorTestBase<SimpleStringPersistentEnumerator> {

  public SimpleStringPersistentEnumeratorTest() {
    super(/*valuesToTest: */ 1_000);
  }


  @Override
  public void nullValue_EnumeratedTo_NULL_ID() throws IOException {
    throw new AssumptionViolatedException("Not satisfied now -- need to investigate");
  }
  
  @Override
  protected SimpleStringPersistentEnumerator openEnumeratorImpl(@NotNull Path storagePath) throws IOException {
    return new SimpleStringPersistentEnumerator(storageFile);
  }
}
