// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.appendonlylog;

import org.junit.*;

import static org.junit.Assert.assertEquals;

public class AppendOnlyLogOverMMappedFile_Internals_Test {

  @Test
  public void anyRecordId_IsEitherRejected_OrConvertedToValidRecordOffset_ThatCouldBeSuccessfullyConvertedBack() {
    int enoughIds = 1 << 20;
    for (long recordId = -enoughIds; recordId < enoughIds; recordId++) {
      long recordOffset;
      try {
        recordOffset = AppendOnlyLogOverMMappedFile.recordIdToOffset(recordId);
      }
      catch (AssertionError | IllegalArgumentException e) {
        continue;//invalid id, OK
      }
      //...but if id is accepted by .recordIdToOffset() -- the offset generated must
      // be accepted by .recordOffsetToId(), and return original recordId
      long id = AppendOnlyLogOverMMappedFile.recordOffsetToId(recordOffset);
      assertEquals(
        recordId + " -> " + recordOffset + " -> " + id,
        recordId,
        id
      );
    }
  }
}