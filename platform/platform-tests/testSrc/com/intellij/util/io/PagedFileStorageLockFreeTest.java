// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.FilePageCacheLockFree.Page;
import org.assertj.core.description.TextDescription;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;


public class PagedFileStorageLockFreeTest {
  public static final int PAGE_SIZE = 8192;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();

  private StorageLockContext storageContext;

  @BeforeClass
  public static void beforeClass() throws Exception {
    assumeTrue(
      "LockFree FilePageCache must be enabled: see PageCacheUtils.LOCK_FREE_VFS_ENABLED",
      PageCacheUtils.LOCK_FREE_VFS_ENABLED
    );
  }

  @Before
  public void setUp() throws Exception {
    storageContext = new StorageLockContext();
  }

  @After
  public void tearDown() throws Exception {
    //storageContext.pageCache().close();
  }

  @Test
  public void attemptToCreate_SecondStorageForSameFile_ThrowsException() throws Exception {
    final File file = tmpDirectory.newFile();
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      assertThatExceptionOfType(IOException.class)
        .describedAs(new TextDescription("Attempt to open second PagedStorage for the same file must fail"))
        .isThrownBy(() -> {
          openFile(file);
        });
    }
  }

  @Test
  public void singleValueWrittenToStorage_CouldBeReadBack() throws Exception {
    final File file = tmpDirectory.newFile();

    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      final long valueToWrite = Long.MAX_VALUE;
      pagedStorage.putLong(0, valueToWrite);
      final long valueReadBack = pagedStorage.getLong(0);
      assertThat(valueReadBack)
        .withFailMessage("Value read back must be the same as the one written")
        .isEqualTo(valueToWrite);
    }
  }

  @Test
  public void closedStorageCouldBeReopenedAgainImmediately() throws Exception {
    final File file = tmpDirectory.newFile();
    for (int tryNo = 0; tryNo < 1000; tryNo++) {
      try (PagedFileStorageLockFree storage = new PagedFileStorageLockFree(file.toPath(), storageContext, PAGE_SIZE, true)) {
      }
    }
  }

  //FIXME RC: test is flaky due to racy .flush() which competes with eager flushes in housekeeper thread
  @Test
  public void storageBecomesDirty_AsValueWrittenToIt_AndBecomeNotDirtyAfterFlush() throws Exception {
    final File file = tmpDirectory.newFile();

    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      assertThat(pagedStorage.isDirty())
        .describedAs("Storage fresh open is not dirty")
        .isFalse();

      pagedStorage.putLong(0, Long.MAX_VALUE);

      assertThat(pagedStorage.isDirty())
        .describedAs("Storage must be dirty as value was written into it")
        .isTrue();

      pagedStorage.force();

      final boolean dirty = pagedStorage.isDirty();
      assertThat(dirty)
        .describedAs("Storage must NOT be dirty: .force() was just called")
        .isFalse();
    }
  }

  @Test
  public void singleValueWrittenToStorage_CouldBeReadBackAfterFileReopened() throws Exception {
    final File file = tmpDirectory.newFile();

    final long valueToWrite = Long.MAX_VALUE;
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      pagedStorage.putLong(0, valueToWrite);
    }

    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      final long valueReadBack = pagedStorage.getLong(0);
      assertThat(valueReadBack)
        .withFailMessage("Value read back must be the same as the one written")
        .isEqualTo(valueToWrite);
    }
  }

  @Test
  public void fewPagesOfValuesWrittenToStorage_CouldBeReadBackAfterFileReopened() throws Exception {
    final File file = tmpDirectory.newFile();
    final int intsCount = PAGE_SIZE * 16 + PAGE_SIZE / 6;

    final int[] valuesToWrite = ThreadLocalRandom.current()
      .ints(intsCount)
      .toArray();

    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      for (int i = 0; i < valuesToWrite.length; i++) {
        final int value = valuesToWrite[i];
        final long offsetInFile = i * (long)Integer.BYTES;
        pagedStorage.putInt(offsetInFile, value);
      }
    }

    final int[] valuesReadBack = new int[intsCount];
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      for (int i = 0; i < valuesReadBack.length; i++) {
        final long offsetInFile = i * (long)Integer.BYTES;
        valuesReadBack[i] = pagedStorage.getInt(offsetInFile);
      }
      assertThat(valuesReadBack)
        .withFailMessage("Values written -- must be read back")
        .isEqualTo(valuesToWrite);
    }
  }

  @Test
  public void storageLength_ReportsTheSizeOfDataWritten() throws Exception {
    final File file = tmpDirectory.newFile();
    final int lengthInBytes = ((PAGE_SIZE * 16 + PAGE_SIZE / 6) / Integer.BYTES) * Integer.BYTES; //+ test part of the page

    final int[] valuesToWrite = ThreadLocalRandom.current()
      .ints(lengthInBytes / Integer.BYTES)
      .toArray();

    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      for (int i = 0; i < valuesToWrite.length; i++) {
        final int value = valuesToWrite[i];
        final long offsetInFile = i * (long)Integer.BYTES;
        pagedStorage.putInt(offsetInFile, value);
        assertThat(offsetInFile + Integer.BYTES)
          .describedAs("Storage.length() must return the length of data written")
          .isEqualTo(pagedStorage.length());
      }
    }
  }

  //TODO RC: getAfterCurrentSize -- what happens?

  @Test
  public void sizeOfStorageFileOnDisk_IsTheSizeOfDataWritten() throws Exception {
    final File file = tmpDirectory.newFile();
    final int lengthInBytes = ((PAGE_SIZE * 16 + PAGE_SIZE / 6) / Integer.BYTES) * Integer.BYTES; //+ test part of the page

    final int[] valuesToWrite = ThreadLocalRandom.current()
      .ints(lengthInBytes / Integer.BYTES)
      .toArray();

    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      for (int i = 0; i < valuesToWrite.length; i++) {
        final int value = valuesToWrite[i];
        final long offsetInFile = i * (long)Integer.BYTES;
        pagedStorage.putInt(offsetInFile, value);
      }
    }
    assertThat(lengthInBytes)
      .withFailMessage("File length must be the length of data written")
      .isEqualTo(file.length());
  }

  @Test//checking page reclaiming and reallocation
  public void moreThanCacheCapacity_CouldBeWrittenIntoSingleStorage() throws Exception {
    final File file = tmpDirectory.newFile();
    final long lengthInBytes = storageContext.pageCache().getCacheCapacityBytes() * 2;

    final byte valueToWrite = (byte)1;
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      final int pageSize = pagedStorage.getPageSize();
      for (long offsetInFile = pageSize - 1; offsetInFile < lengthInBytes; offsetInFile += pageSize) {
        pagedStorage.put(offsetInFile, valueToWrite);
      }
    }

    //reopen:
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      final int pageSize = pagedStorage.getPageSize();
      for (long offsetInFile = pageSize - 1; offsetInFile < lengthInBytes; offsetInFile += pageSize) {
        final byte valueRead = pagedStorage.get(offsetInFile);
        assertThat(valueRead)
          .withFailMessage("Bytes written must be read back")
          .isEqualTo(valueToWrite);
      }
    }
  }

  /** @see PagedStorageWithPageUnalignedAccess */
  @Test
  public void pageUnAlignedPrimitiveAccesses_ThrowException() throws IOException, InterruptedException {
    final File file = tmpDirectory.newFile();
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      final int pageSize = pagedStorage.getPageSize();

      assertThatIOException()
        .describedAs(new TextDescription("Put long on a page border must throw exception"))
        .isThrownBy(() -> {
          pagedStorage.putLong(pageSize - 2, Long.MAX_VALUE);
        });

      assertThatIOException()
        .describedAs(new TextDescription("Get long on a page border must throw exception"))
        .isThrownBy(() -> {
          pagedStorage.getLong(pageSize - 2);
        });

      assertThatIOException()
        .describedAs(new TextDescription("Put int on a page border must throw exception"))
        .isThrownBy(() -> {
          pagedStorage.putInt(pageSize - 1, Integer.MAX_VALUE);
        });


      assertThatIOException()
        .describedAs(new TextDescription("Get int on a page border must throw exception"))
        .isThrownBy(() -> {
          pagedStorage.getInt(pageSize - 1);
        });

      //buffer is not a primitive: crossing page boundary is just not implemented for it (yet?)
      assertThatIOException()
        .describedAs(new TextDescription("Put buffer on a page border is not yet implemented -> must throw exception"))
        .isThrownBy(() -> {
          final ByteBuffer buffer = ByteBuffer.allocate(10);
          pagedStorage.putBuffer(pageSize - 1, buffer);
        });
    }
  }

  @Test
  public void uncontendedMultiThreadedWrites_ReadBackUnchanged_Playground() throws IOException, InterruptedException {
    final long cacheCapacityBytes = storageContext.pageCache().getCacheCapacityBytes();
    final int pagesInCache = (int)(cacheCapacityBytes / PAGE_SIZE);
    final int fileSize = (2 * pagesInCache + 20) * PAGE_SIZE;
    final int blockSize = 64;
    final int threads = Runtime.getRuntime().availableProcessors() - 1;

    final ExecutorService pool = Executors.newFixedThreadPool(threads);

    assertEquals(
      "N stamps must fit into a page",
      0,
      PAGE_SIZE % blockSize
    );

    final File file = tmpDirectory.newFile();
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      try {
        final List<Future<Void>> futures = IntStream.range(0, threads)
          .mapToObj(threadNo -> (Callable<Void>)() -> {
            for (long offset = threadNo * blockSize;
                 offset < fileSize;
                 offset += threads * blockSize) {
              try (final Page page = pagedStorage.pageByOffset(offset, true)) {
                final int offsetOnPage = pagedStorage.toOffsetInPage(offset);
                page.putLong(offsetOnPage, threadNo + 1);
              }
            }
            return null;
          })
          .map(pool::submit)
          .toList();
      }
      finally {
        pool.shutdown();
        assertTrue(
          "Must terminated in 10 min",
          pool.awaitTermination(10, TimeUnit.MINUTES)
        );
      }
    }
    
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      //now try to read it back:
      boolean fail = false;
      for (long offset = 0; offset < fileSize; offset += blockSize) {
        try (final Page page = pagedStorage.pageByOffset(offset, false)) {
          final int offsetInPage = pagedStorage.toOffsetInPage(offset);
          final long value = page.getLong(offsetInPage);
          final long expectedValue = (offset / blockSize) % threads + 1;
          if (value != expectedValue) {
            final String fullPageContent = page.read(
              offsetInPage, blockSize,
              pageBuffer -> {
                return IOUtil.toHexString(pageBuffer, /*paginate: */ blockSize);
              }
            );
            fail = true;
            System.out.println("page[" + page.pageIndex() + " x " + PAGE_SIZE + " +" + offsetInPage + " = " + offset + "]: \n" +
                               "expected: " + expectedValue + "\n" +
                               " but was: " + value + "\n" +
                               "storage.length: " + pagedStorage.length() + "\n" +
                               "cacheCapacity:  " + cacheCapacityBytes + "\n" +
                               "full page content: \n" + fullPageContent);
          }
        }
      }
      if (fail) {
        fail("");
      }
    }
  }


  @Test
  public void uncontendedMultiThreadedWrites_ReadBackUnchanged() throws IOException, InterruptedException {
    final int pagesInCache = (int)(storageContext.pageCache().getCacheCapacityBytes() / PAGE_SIZE);
    final int fileSize = (2 * pagesInCache + 20) * PAGE_SIZE;
    final int blockSize = 64;
    final int threads = Runtime.getRuntime().availableProcessors() - 1;

    final ExecutorService pool = Executors.newFixedThreadPool(threads);

    final byte[] stamp = generateRandomByteArray(blockSize);
    assertEquals(
      "N stamps must fit into a page",
      0,
      PAGE_SIZE % stamp.length
    );

    final File file = tmpDirectory.newFile();
    try (final PagedFileStorageLockFree pagedStorage = openFile(file)) {
      try {
        final List<Future<Void>> futures = IntStream.range(0, threads)
          .mapToObj(threadNo -> (Callable<Void>)() -> {
            for (long offset = threadNo * stamp.length;
                 offset < fileSize;
                 offset += threads * stamp.length) {
              try (final Page page = pagedStorage.pageByOffset(offset, true)) {
                final int offsetOnPage = pagedStorage.toOffsetInPage(offset);
                page.write(
                  offsetOnPage, stamp.length,
                  pageBuffer -> {
                    return pageBuffer.put(stamp);
                  });
              }
            }
            return null;
          })
          .map(pool::submit)
          .toList();
      }
      finally {
        pool.shutdown();
        assertTrue(
          "Must terminated in 10 min",
          pool.awaitTermination(10, TimeUnit.MINUTES)
        );
      }

      //now try to read it back:
      final byte[] buffer = new byte[stamp.length];
      for (long offset = 0; offset < fileSize; offset += stamp.length) {
        try (final Page page = pagedStorage.pageByOffset(offset, false)) {
          final int offsetInPage = pagedStorage.toOffsetInPage(offset);
          page.read(
            offsetInPage, stamp.length,
            pageBuffer -> {
              return pageBuffer.get(buffer);
            }
          );
          if (!Arrays.equals(buffer, stamp)) {
            final String fullPageContent = page.read(
              offsetInPage, stamp.length,
              pageBuffer -> {
                return IOUtil.toHexString(pageBuffer, /*paginate: */ blockSize);
              }
            );
            fail("page[" + page.pageIndex() + " x " + PAGE_SIZE + " +" + offsetInPage + " = " + offset + "]: \n" +
                 "expected: " + IOUtil.toHexString(stamp) + "\n" +
                 " but was: " + IOUtil.toHexString(buffer) + "\n" +
                 "storage.length: " + pagedStorage.length() + "\n" +
                 "full page content: \n" + fullPageContent);
          }
          //clean buffer before next turn:
          Arrays.fill(buffer, (byte)0);
        }
      }
    }
  }


  @NotNull
  private PagedFileStorageLockFree openFile(final @NotNull File file) throws IOException {
    return new PagedFileStorageLockFree(
      file.toPath(),
      storageContext,
      PAGE_SIZE,
      true
    );
  }

  private static byte @NotNull [] generateRandomByteArray(final int length) {
    final byte[] stamp = new byte[length];
    for (int i = 0; i < stamp.length; i++) {
      stamp[i] = (byte)ThreadLocalRandom.current().nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
    return stamp;
  }
}