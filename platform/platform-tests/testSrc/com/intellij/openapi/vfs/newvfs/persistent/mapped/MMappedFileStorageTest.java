// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.mapped;

import com.intellij.openapi.vfs.newvfs.persistent.mapped.MMappedFileStorage.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MMappedFileStorageTest {

  public static final int PAGE_SIZE = 1 << 20;

  private MMappedFileStorage storage;

  @BeforeEach
  public void setup(@TempDir Path tempDir) throws IOException {
    storage = new MMappedFileStorage(tempDir.resolve("test.mmap").toAbsolutePath(), PAGE_SIZE);
  }

  @AfterEach
  public void tearDown() throws Exception {
    storage.close();
  }


  @Test
  public void contentOfNewlyAllocatedPage_MustBeZero() throws Exception {
    for (int attempt = 0; attempt < 16; attempt++) {
      int randomPageNo = ThreadLocalRandom.current().nextInt(0, 32);
      Page page = storage.pageByOffset((long)PAGE_SIZE * randomPageNo);
      ByteBuffer buffer = page.rawPageBuffer();
      for (int pos = 0; pos < PAGE_SIZE; pos++) {
        assertEquals(
          0,
          buffer.get(pos),
          "Every byte must be zero"
        );
      }
    }
  }

  @Test
  public void zeroRegion_makesAllBytesInRegionZero_ButDoestTouchBytesOutsideTheRegion() throws Exception {
    byte filler = (byte)0xFF;
    for (int pageNo = 0; pageNo < 16; pageNo++) {
      Page page = storage.pageByOffset((long)PAGE_SIZE * pageNo);
      ByteBuffer buffer = page.rawPageBuffer();
      for (int pos = 0; pos < PAGE_SIZE; pos++) {
        buffer.put(pos, filler);
      }
    }

    int startOffsetInFile = PAGE_SIZE / 2;
    int endOffsetInFile = PAGE_SIZE * 5 / 2;
    storage.zeroRegion(startOffsetInFile, endOffsetInFile);

    for (long pos = 0; pos < startOffsetInFile; pos++) {
      int offsetInPage = storage.toOffsetInPage(pos);
      Page page = storage.pageByOffset(pos);
      ByteBuffer buffer = page.rawPageBuffer();
      assertEquals(filler, buffer.get(offsetInPage), "all bytes before zeroed region must be NOT 0");
    }

    for (long pos = startOffsetInFile; pos <= endOffsetInFile; pos++) {
      int offsetInPage = storage.toOffsetInPage(pos);
      Page page = storage.pageByOffset(pos);
      ByteBuffer buffer = page.rawPageBuffer();
      assertEquals(0, buffer.get(offsetInPage), "all bytes in zeroed region must be 0");
    }

    for (long pos = endOffsetInFile + 1; pos < endOffsetInFile + PAGE_SIZE; pos++) {
      int offsetInPage = storage.toOffsetInPage(pos);
      Page page = storage.pageByOffset(pos);
      ByteBuffer buffer = page.rawPageBuffer();
      assertEquals(filler, buffer.get(offsetInPage), "all bytes after zeroed region must be NOT 0");
    }
  }
}