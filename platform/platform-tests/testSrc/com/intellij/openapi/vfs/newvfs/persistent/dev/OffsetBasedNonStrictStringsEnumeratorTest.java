// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class OffsetBasedNonStrictStringsEnumeratorTest extends NonStrictStringsEnumeratorTestBase<OffsetBasedNonStrictStringsEnumerator> {
  @Test
  public void tryEnumerateRightAfterEnumerateShouldReturnSameId() throws IOException {
    for (String value : manyValues) {
      final int enumeratedId = enumerator.enumerate(value);
      final int tryEnumerateId = enumerator.tryEnumerate(value);
      assertEquals(
        "value[" + value + "] just enumerated should be tryEnumerate()-ed to the same id",
        enumeratedId,
        tryEnumerateId
      );
    }
  }

  @Override
  protected OffsetBasedNonStrictStringsEnumerator openEnumerator(@NotNull Path storagePath) throws IOException {
    final ResizeableMappedFile mappedFile = new ResizeableMappedFile(
      storagePath,
      10 * PagedFileStorage.MB,
      null,
      -1 /*use default page size*/,
      false
    );
    return new OffsetBasedNonStrictStringsEnumerator(mappedFile);
  }
}
