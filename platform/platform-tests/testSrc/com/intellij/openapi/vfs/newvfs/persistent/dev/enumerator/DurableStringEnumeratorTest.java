// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.vfs.newvfs.persistent.dev.StringEnumeratorTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.util.io.DataEnumeratorEx.NULL_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DurableStringEnumeratorTest extends StringEnumeratorTestBase<DurableStringEnumerator> {

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

  @Override
  protected DurableStringEnumerator openEnumerator(@NotNull Path storagePath) throws IOException {
    return new DurableStringEnumerator(storagePath);
  }
}