// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.FilePageCacheLockFree.Page;
import com.intellij.util.io.FilePageCacheLockFree.PagesTable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 *
 */
public class PagesTableTest {

  private static final int PAGE_SIZE = 4096;
  private static final int PAGES_TO_CREATE = 8 * 1024;

  @Test
  public void tableCreatesPagesPerRequest() throws IOException {
    final PagesTable pages = new PagesTable(8);
    final int pagesToCreate = 8 * 1024;
    for (int pageIndex = 0; pageIndex < pagesToCreate; pageIndex++) {
      final Page page = pages.lookupOrCreate(
        pageIndex,
        PagesTableTest::createBlankPage,
        PagesTableTest::allocateAndLoadPage
      );

      assertThat(page)
        .describedAs("PagesTable must return existent page, or create new one")
        .isNotNull();

      assertThat(page.pageSize())
        .describedAs("Page must be created by .createBlankPage() with default PAGE_SIZE")
        .isEqualTo(PAGE_SIZE);

      assertThat(page.pageIndex())
        .describedAs("Page must be have index=" + pageIndex)
        .isEqualTo(pageIndex);

      assertThat(page.isUsable())
        .describedAs("Page must be valid (=initialized)")
        .isTrue();
    }
  }

  @Test
  public void tableReturnsAlreadyCreatedPagesOnSubsequentRequests() throws IOException {
    final PagesTable pages = new PagesTable(8);
    final Page[] pagesCreated = new Page[PAGES_TO_CREATE];
    for (int pageIndex = 0; pageIndex < PAGES_TO_CREATE; pageIndex++) {
      final Page page = pages.lookupOrCreate(
        pageIndex,
        PagesTableTest::createBlankPage,
        PagesTableTest::allocateAndLoadPage
      );
      pagesCreated[pageIndex] = page;
    }

    for (int pageIndex = 0; pageIndex < PAGES_TO_CREATE; pageIndex++) {
      final Page page = pages.lookupOrCreate(
        pageIndex,
        index -> fail("Should not create new page " + index + " -> should return already created one"),
        _page -> fail("Should not load page " + _page + " -> should return already loaded one")
      );

      assertThat(page)
        .describedAs("PagesTable must return existent page, created on first access")
        .isEqualTo(pagesCreated[pageIndex]);
    }
  }

  @Test
  public void lookupOrCreateReturnsSamePageForConcurrentRequests() throws Exception {
    final PagesTable pages = new PagesTable(8);

    final Thread[] threads = new Thread[Runtime.getRuntime().availableProcessors()];
    final int pagesToCreate = 100_000;
    final Page[][] pagesCreated = new Page[threads.length][pagesToCreate];

    //RC: try to create a lot of concurrency around new page initialization, so bugs there have
    //    the best chance to show themselves -- i.e. I want all threads hit pages.lookupOrCreate(pageIndex)
    //    almost the same moment. For this, I don't want to park/unpark threads, since thread wake up
    //    takes microseconds at least, and lookup itself takes much less, so it reduces concurrency.
    //    Instead, threads will wait in busy-spin, and coordinate to use the same page index with
    //    shared volatile sharedPageIndexBarrier.
    final AtomicInteger sharedPageIndexBarrier = new AtomicInteger(-1);
    final AtomicInteger threadsOnBarrier = new AtomicInteger(0);
    final AtomicReference<Exception> errorHolder = new AtomicReference<>();
    for (int i = 0; i < threads.length; i++) {
      final Page[] pagesCreatedByThread = pagesCreated[i];
      threads[i] = new Thread("Racer:" + i) {
        private int currentThreadPageIndex = 0;

        @Override
        public void run() {
          try {
            while (currentThreadPageIndex < pagesCreated.length) {
              if (currentThreadPageIndex == sharedPageIndexBarrier.get()) {
                pagesCreatedByThread[currentThreadPageIndex] = pages.lookupOrCreate(
                  currentThreadPageIndex,
                  PagesTableTest::createBlankPage,
                  PagesTableTest::allocateAndLoadPage
                );

                final int threadsAlreadyFinishedWithPage = threadsOnBarrier.incrementAndGet();
                //the last thread to come to the barrier -- moves the barrier farther:
                if (threadsAlreadyFinishedWithPage == threads.length) {
                  threadsOnBarrier.set(0);
                  sharedPageIndexBarrier.incrementAndGet();
                }
                currentThreadPageIndex++;
              }
            }
          }
          catch (IOException e) {
            errorHolder.set(e);
          }
        }
      };
    }

    for (Thread thread : threads) {
      thread.start();
    }
    sharedPageIndexBarrier.set(0);//open the barrier
    for (Thread thread : threads) {
      thread.join();
    }
    if (errorHolder.get() != null) {
      throw errorHolder.get();
    }

    for (int pageNo = 0; pageNo < pagesToCreate; pageNo++) {
      final HashSet<Page> pagesWithSameNo = new HashSet<>();
      for (int threadNo = 0; threadNo < threads.length; threadNo++) {
        final Page page = pagesCreated[threadNo][pageNo];
        pagesWithSameNo.add(page);
      }
      assertThat(pagesWithSameNo)
        .describedAs("All threads must get same instance of Page[#" + pageNo + "]")
        .hasSize(1);
    }
  }

  @Test
  public void entombedPagesAreNotReturnedByLookups() throws IOException {
    final PagesTable pages = new PagesTable(8);
    for (int pageIndex = 0; pageIndex < PAGES_TO_CREATE; pageIndex++) {
      final Page page = pages.lookupOrCreate(
        pageIndex,
        PagesTableTest::createBlankPage,
        PagesTableTest::allocateAndLoadPage
      );
      //entomb the page:
      page.tryMoveTowardsTomb(false);

      assertThat(pages.lookupIfExist(pageIndex))
        .describedAs("TOMBSTONEs are ignored for lookups")
        .isNull();

      final Page pageForSameIndex = pages.lookupOrCreate(
        pageIndex,
        PagesTableTest::createBlankPage,
        PagesTableTest::allocateAndLoadPage
      );
      assertThat(pageForSameIndex)
        .describedAs("TOMBSTONEs are overwritten .lookupOrCreate()")
        .isNotEqualTo(page);
    }
  }

  //=========== infrastructure:

  private static Page createBlankPage(final int pageIndex) {
    return Page.notReady(
      pageIndex,
      PAGE_SIZE,
      flusher
    );
  }

  private static ByteBuffer allocateAndLoadPage(final @NotNull Page page) {
    return ByteBuffer.allocate(0);
  }

  private static final FilePageCacheLockFree.PageToStorageHandle flusher = new FilePageCacheLockFree.PageToStorageHandle() {
    @Override
    public void pageBecomeDirty() {
      throw new UnsupportedOperationException("Not implemented in this test");
    }

    @Override
    public void pageBecomeClean() {
      throw new UnsupportedOperationException("Not implemented in this test");
    }

    @Override
    public void modifiedRegionUpdated(long startOffsetInFile, int length) {
      throw new UnsupportedOperationException("Not implemented in this test");
    }

    @Override
    public void flushBytes(final @NotNull ByteBuffer dataToFlush,
                           final long offsetInFile) throws IOException {
      throw new UnsupportedOperationException("Flush is not implemented in this test");
    }
  };
}