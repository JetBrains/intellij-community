// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.pagecache.Page;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import org.assertj.core.description.TextDescription;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(Parameterized.class)
public class PagedFileStorageWithRWLockedPageContentTest {
  private static final int THREADS = Runtime.getRuntime().availableProcessors() - 1;//leave 1 CPU for 'main'

  private static final int CACHE_CAPACITY_BYTES = 100 << 20;//100Mb

  private static final int PAGE_SIZE = 8192;

  private static final int ENOUGH_TRIES = 10_000;


  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();


  private static FilePageCacheLockFree filePageCache;

  private StorageLockContext storageContext;

  private final PageContentLockingStrategy lockingStrategy;

  @Parameterized.Parameters(name = "{index}: {0}")
  public static List<PageContentLockingStrategy> strategiesToTry() {
    return List.of(
      PageContentLockingStrategy.LOCK_PER_PAGE,
      new PageContentLockingStrategy.SharedLockLockingStrategy()
    );
  }

  public PagedFileStorageWithRWLockedPageContentTest(@NotNull PageContentLockingStrategy strategy) { lockingStrategy = strategy; }

  @BeforeClass
  public static void beforeClass() throws Exception {
    filePageCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    if (filePageCache != null) {
      filePageCache.close();
    }
  }

  @Before
  public void setUp() throws Exception {
    storageContext = new StorageLockContext(filePageCache, true, true, false);
  }

  // ====================== tests:     ==============================================================

  @Test
  public void attemptToCreate_SecondStorageForSameFile_ThrowsException() throws Exception {
    final File file = tmpDirectory.newFile();
    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
      assertThatExceptionOfType(IOException.class)
        .describedAs(new TextDescription("Attempt to open second PagedStorage for the same file must fail"))
        .isThrownBy(() -> {
          final PagedFileStorageWithRWLockedPageContent secondStorageForSameFile = openFile(file);
          secondStorageForSameFile.close();
        });
    }
  }

  @Test
  public void singleValueWrittenToStorage_CouldBeReadBack() throws Exception {
    final File file = tmpDirectory.newFile();

    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
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
    for (int tryNo = 0; tryNo < ENOUGH_TRIES; tryNo++) {
      final PagedFileStorageWithRWLockedPageContent
        storage =
        new PagedFileStorageWithRWLockedPageContent(file.toPath(), storageContext, PAGE_SIZE, true,
                                                    PageContentLockingStrategy.LOCK_PER_PAGE);
      storage.close();
    }
  }

  @Test
  public void storageBecomesDirty_AsValueWrittenToIt_AndBecomeNotDirtyAfterFlush() throws Exception {
    final File file = tmpDirectory.newFile();

    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
      assertThat(pagedStorage.isDirty())
        .describedAs("Storage fresh open is not dirty")
        .isFalse();

      pagedStorage.putLong(0, Long.MAX_VALUE);

      //RC: housekeeper thread is allowed to flush pages to unmap & reclaim the buffers, so it _could
      //    be_ pagedStorage is !dirty even though something was just written into it.
      //    But FilePageCache _should not_ unmap page without reason, and in this case there is no
      //    competing pressure on the FPC that could create such a reason -- so I expect the invariant
      //    to hold
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
    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
      pagedStorage.putLong(0, valueToWrite);
    }

    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
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

    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
      for (int i = 0; i < valuesToWrite.length; i++) {
        final int value = valuesToWrite[i];
        final long offsetInFile = i * (long)Integer.BYTES;
        pagedStorage.putInt(offsetInFile, value);
      }
    }

    final int[] valuesReadBack = new int[intsCount];
    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
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

    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
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

    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
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
    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
      final int pageSize = pagedStorage.getPageSize();
      for (long offsetInFile = pageSize - 1; offsetInFile < lengthInBytes; offsetInFile += pageSize) {
        pagedStorage.put(offsetInFile, valueToWrite);
      }
    }

    //reopen:
    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
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
    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
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

  @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
  @Test
  public void uncontendedMultiThreadedWrites_ReadBackUnchanged() throws IOException, InterruptedException {
    final int pagesInCache = (int)(storageContext.pageCache().getCacheCapacityBytes() / PAGE_SIZE);
    final int fileSize = (2 * pagesInCache + 20) * PAGE_SIZE;
    final int blockSize = 64;
    final File file = tmpDirectory.newFile();

    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      final byte[] stamp = generateRandomByteArray(blockSize);
      assertEquals("N stamps must fit into a page", 0, PAGE_SIZE % stamp.length);

      try (final PagedFileStorageWithRWLockedPageContent pagedStorage = openFile(file)) {
        try {
          final List<Future<Void>> futures = IntStream.range(0, THREADS)
            .mapToObj(threadNo -> (Callable<Void>)() -> {
              for (long offset = threadNo * stamp.length;
                   offset < fileSize;
                   offset += THREADS * stamp.length) {
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
              fail("page[" +
                   page.pageIndex() +
                   " x " +
                   PAGE_SIZE +
                   " +" +
                   offsetInPage +
                   " = " +
                   offset +
                   "]: \n" +
                   "expected: " +
                   IOUtil.toHexString(stamp) +
                   "\n" +
                   " but was: " +
                   IOUtil.toHexString(buffer) +
                   "\n" +
                   "storage.length: " +
                   pagedStorage.length() +
                   "\n" +
                   "full page content: \n" +
                   fullPageContent);
            }
            //clean buffer before next turn:
            Arrays.fill(buffer, (byte)0);
          }
        }
      }
    }
    finally {
      pool.shutdown();
      assertTrue(
        "Pool must terminate in 1 sec",
        pool.awaitTermination(1, SECONDS)
      );
    }
  }

  @Test//(timeout = 100_000L)
  public void closeOfStorage_SuccessfullyClosesPages_EvenInTheMiddleOfPageInitialization() throws Exception {
    final int threads = Runtime.getRuntime().availableProcessors() - 1;
    final File file = tmpDirectory.newFile();

    final ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int tryNo = 0; tryNo < ENOUGH_TRIES; tryNo++) {
        final List<Future<Void>> futures;
        try (PagedFileStorageWithRWLockedPageContent storage = openFile(file)) {
          final CountDownLatch latch = new CountDownLatch(1);
          futures = IntStream.range(0, threads)
            .<Callable<Void>>mapToObj(pageNo -> () -> {
              latch.await();
              try (Page page = storage.pageByIndex(pageNo, true)) {
                //do nothing, just get the page from storage
                page.isUsable();
              }
              return null;
            })
            .map(pool::submit)
            .toList();

          latch.countDown();
        }
        // -> Now storage is closed, and we should get _no_ AssertionErrors or IllegalStateExceptions
        //    from futures. But we _could- get ~ IOException("...already closed")
        for (Future<Void> future : futures) {
          try {
            future.get();
          }
          catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException exception) {
              if (exception.getMessage().contains("already closed")) {
                //ok, executable
                continue;
              }
            }
            throw e;
          }
        }
      }
    }
    finally {
      pool.shutdown();
      assertTrue(
        "Pool must terminate in 1 sec",
        pool.awaitTermination(1, SECONDS)
      );
    }
  }


  // ====================== infrastructure:  ==============================================================

  @NotNull
  private PagedFileStorageWithRWLockedPageContent openFile(final @NotNull File file) throws IOException {
    return new PagedFileStorageWithRWLockedPageContent(
      file.toPath(),
      storageContext,
      PAGE_SIZE,
      true,
      PageContentLockingStrategy.LOCK_PER_PAGE
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