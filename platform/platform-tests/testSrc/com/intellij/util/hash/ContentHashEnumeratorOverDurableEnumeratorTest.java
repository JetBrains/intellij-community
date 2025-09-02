// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.hash;

import com.intellij.openapi.vfs.newvfs.persistent.mapped.content.ContentHashEnumeratorOverDurableEnumerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.util.io.DataEnumerator.NULL_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ContentHashEnumeratorOverDurableEnumeratorTest extends ContentHashEnumeratorTestBase {
  @TempDir
  private Path tempDir;

  @Override
  protected ContentHashEnumerator openEnumerator() throws IOException {
    Path storagePath = tempDir.resolve("enumerator_mmap");
    return ContentHashEnumeratorOverDurableEnumerator.open(storagePath);
  }

  @Test
  void hashToEnumeratorId_isReversibleTransform() {
    ContentHashEnumeratorOverDurableEnumerator e = (ContentHashEnumeratorOverDurableEnumerator)enumerator;
    int maxId = Integer.MAX_VALUE / (ContentHashEnumerator.SIGNATURE_LENGTH + 4);
    for (int hashId = NULL_ID; hashId < maxId; hashId++) {
      int enumeratorId = e.hashIdToEnumeratorId(hashId);
      assertTrue(
        enumeratorId >= 0,
        "enumeratorId(=" + enumeratorId + ") must be >=0 (hashId: " + hashId + ", maxId: " + maxId + ")"
      );
      int reEvaluatedHashId = e.enumeratorIdToHashId(enumeratorId);
      assertEquals(hashId, reEvaluatedHashId,
                   () -> "re-evaluated hashId must be the same as initial (enumeratorId: " + enumeratorId + ", maxId: " + maxId + ")");
    }
  }
}