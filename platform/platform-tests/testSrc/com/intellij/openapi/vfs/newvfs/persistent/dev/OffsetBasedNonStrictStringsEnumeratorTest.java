// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.io.IOUtil;
import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class OffsetBasedNonStrictStringsEnumeratorTest extends StringEnumeratorTestBase<OffsetBasedNonStrictStringsEnumerator> {
  @Override
  @Ignore("For non-strict enumerator it is not applicable")
  public void forManyValuesEnumerated_SecondTimeEnumerate_ReturnsSameId() throws IOException {
  }

  @Override
  @Ignore("For non-strict enumerator it is not applicable")
  public void forManyValuesEnumerated_TryEnumerate_ReturnsSameId() throws IOException {
  }

  @Override
  @Ignore("For non-strict enumerator it is not applicable")
  public void forManyValuesEnumerated_SecondTimeEnumerate_ReturnsSameId_AfterReload() throws Exception {
  }

  @Override
  @Ignore("For non-strict enumerator it is not applicable")
  public void forManyValuesEnumerated_TryEnumerate_ReturnsSameId_AfterReload() throws Exception {
  }

  @Test
  public void forManyValuesEnumerated_TryEnumerate_CalledRightAfter_ReturnsSameId() throws IOException {
    for (String value : manyValues) {
      int enumeratedId = enumerator.enumerate(value);
      int tryEnumerateId = enumerator.tryEnumerate(value);
      assertEquals(
        "value[" + value + "] just enumerated should be tryEnumerate()-ed to the same id",
        enumeratedId,
        tryEnumerateId
      );
    }
  }

  @Override
  protected OffsetBasedNonStrictStringsEnumerator openEnumerator(@NotNull Path storagePath) throws IOException {
    ResizeableMappedFile mappedFile = new ResizeableMappedFile(
      storagePath,
      10 * IOUtil.MiB,
      null,
      -1 /*use default page size*/,
      false
    );
    return new OffsetBasedNonStrictStringsEnumerator(mappedFile);
  }
}
