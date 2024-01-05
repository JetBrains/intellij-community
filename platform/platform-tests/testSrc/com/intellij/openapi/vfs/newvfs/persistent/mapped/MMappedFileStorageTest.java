// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.mapped;

import com.intellij.util.io.dev.mmapped.MMappedFileStorage;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage.Page;
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class MMappedFileStorageTest {

  public static final int PAGE_SIZE = 1 << 20;

  private MMappedFileStorage storage;
  private Path storagePath;

  @BeforeEach
  public void setup(@TempDir Path tempDir) throws IOException {
    storagePath = tempDir.resolve("test.mmap").toAbsolutePath();
    storage = open();
  }

  private @NotNull MMappedFileStorage open() throws IOException {
    return new MMappedFileStorage(storagePath, PAGE_SIZE);
  }

  @AfterEach
  public void tearDown() throws Exception {
    storage.closeAndClean();
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
    storage.zeroizeRegion(startOffsetInFile, endOffsetInFile);

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

  @Test
  public void pagesByIndex_AlwaysReturnsSamePageInstance_evenUnderMultiThreadedAccess(@TempDir Path tempDir) throws Exception {
    Path storagePath = tempDir.resolve("test.mmap").toAbsolutePath();
    int CPUs = Runtime.getRuntime().availableProcessors();
    int pagesCount = 1024;
    int smallPageSize = 1024;

    ExecutorService pool = Executors.newFixedThreadPool(CPUs);
    try {
      //create dedicated storage with small pages so page-allocation is faster and fewer shadows concurrent issues:
      try (MMappedFileStorage storage = new MMappedFileStorage(storagePath, smallPageSize)) {
        try {
          Future<Page[]>[] futures = new Future[CPUs];

          CountDownLatch startRace = new CountDownLatch(1);
          for (int threadNo = 0; threadNo < CPUs; threadNo++) {
            futures[threadNo] = pool.submit(() -> {
              startRace.await();
              Page[] pages = new Page[pagesCount];
              for (int pageNo = 0; pageNo < pages.length; pageNo++) {
                pages[pageNo] = storage.pageByIndex(pageNo);
              }
              return pages;
            });
          }

          startRace.countDown();

          Page[][] pagesGotByThread = new Page[CPUs][];
          for (int threadNo = 0; threadNo < futures.length; threadNo++) {
            pagesGotByThread[threadNo] = futures[threadNo].get();
          }

          //check: ForAll[pageNo] { pagesGotByThread[*][pageNo] } are the same
          // (i.e. for any pageIndex all threads always got the same Page(pageIndex) instance)
          for (int pageNo = 0; pageNo < pagesCount; pageNo++) {
            for (int threadNo = 0; threadNo < CPUs - 1; threadNo++) {
              Page page1 = pagesGotByThread[threadNo][pageNo];
              Page page2 = pagesGotByThread[threadNo + 1][pageNo];
              if (page1 != page2) {
                fail("[pageNo: " + pageNo + "]: " +
                     "thread[" + threadNo + "] got " + page1 + " != " + page2 + " got by thread[" + (threadNo + 1) + "]");
              }
            }
          }
        }
        finally {
          storage.closeAndClean();
        }
      }
    }
    finally {
      pool.shutdown();
      pool.awaitTermination(10, SECONDS);
    }
  }

  @Test
  public void openingSecondStorage_OverSameFile_Fails() {
    assertThrows(
      IllegalStateException.class,
      () -> new MMappedFileStorage(storage.storagePath(), PAGE_SIZE)
    );
  }

  @Test
  public void closeAndClean_RemovesTheStorageFile() throws IOException {
    //RC: it is over-specification -- .closeAndClean() doesn't require to remove the file, only to clean the
    //    content so new storage opened on top of it will be as-new. But this is the current implementation
    //    of that spec:
    storage.closeAndClean();
    assertFalse(
      Files.exists(storage.storagePath()),
      "Storage file [" + storage.storagePath() + "] must not exist after .closeAndClean()"
    );
  }

  @Test
  public void openedStoragesCount_isEqualToNumberOfOpenedAndNotClosedStorages() throws IOException {
    int initialStoragesCount = MMappedFileStorage.openedStoragesCount();

    storage.close();
    assertEquals(initialStoragesCount - 1, MMappedFileStorage.openedStoragesCount(),
                 "One storage is closed, so openedStoragesCount must be decremented");

    MMappedFileStorage anotherStorageOtherSameFile = open();
    assertEquals(initialStoragesCount, MMappedFileStorage.openedStoragesCount(),
                 "Another storage is opened, so openedStoragesCount must be incremented");

    storage.closeAndUnsafelyUnmap();
    assertEquals(initialStoragesCount, MMappedFileStorage.openedStoragesCount(),
                 "'storage' was already closed, unmapping it doesn't decrement openedStoragesCount even more");

    anotherStorageOtherSameFile.closeAndClean();
    assertEquals(initialStoragesCount - 1, MMappedFileStorage.openedStoragesCount(),
                 "'another storage' is now closed -> openedStoragesCount must be decremented");
  }

  @Test
  public void fsync_IsSafeToCallOnStorage() throws IOException {
    //IDEA-335858: IOException('Resource busy') on MacOS M2
    //             just check .fsync() is not fail on all platforms
    int pagesToAllocate = 16;
    byte[] ones = new byte[1024];
    Arrays.fill(ones, (byte)1);
    for (int pageNo = 0; pageNo < pagesToAllocate; pageNo++) {
      Page page = storage.pageByIndex(pageNo);
      ByteBuffer buffer = page.rawPageBuffer();
      buffer.put(0, ones);
    }

    storage.fsync();
  }

  @Test
  public void mappedStorageFailsOpenStorage_IfStorageParentDirectoryNotExist(@TempDir Path tempDir) {
    Path nonExistentDir = tempDir.resolve("subdir");
    Path storagePath = nonExistentDir.resolve("storage.file").toAbsolutePath();
    try {
      var storage = new MMappedFileStorage(storagePath, PAGE_SIZE);
      storage.closeAndClean();
      fail("Storage must fail to open file in non-existing directory");
    }
    catch (IOException e) {
      //ok
    }
  }

  @Test
  public void mappedStorage_Factory_CreatesParentDirectoryIfNotExist(@TempDir Path tempDir) throws IOException {
    Path nonExistentDir = tempDir.resolve("subdir");
    Path storagePath = nonExistentDir.resolve("storage.file").toAbsolutePath();
    try (var storage = MMappedFileStorageFactory.withDefaults().open(storagePath)) {
      storage.closeAndClean();
    }
  }
}