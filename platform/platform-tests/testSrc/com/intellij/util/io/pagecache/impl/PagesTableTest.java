// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.util.io.pagecache.Page;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

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
    final PageImpl[][] pagesCreated = new PageImpl[threads.length][pagesToCreate];

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
      final PageImpl page = pages.lookupOrCreate(
        pageIndex,
        PagesTableTest::createBlankPage,
        PagesTableTest::allocateAndLoadPage
      );
      //entomb the page:
      page.tryMoveTowardsPreTombstone(false);
      page.entomb();

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

  @Test
  public void hashTableProbeLengthsAreNotTooLong() throws IOException {
    //It is not a comprehensive hash-table test, just a sanity check: verify that hash table
    // implementation is not degenerate due to silly mistakes or unlucky combination of params.
    //For 3 different distributions of pages (sequential, random, random-blocks)
    // check that >=95% of entries could be found in (0,1,2) probes:
    final double expectedWeightOf3Probes = 0.95;


    final int[] sequentialPageIndexes = IntStream.rangeClosed(0, PAGES_TO_CREATE)
      .toArray();
    checkProbeLengthsAreShort("Sequential pages", sequentialPageIndexes, expectedWeightOf3Probes);

    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final int[] randomPageIndexes = IntStream.rangeClosed(0, PAGES_TO_CREATE)
      .map(i -> rnd.nextInt(2 * PAGES_TO_CREATE))
      .toArray();
    checkProbeLengthsAreShort("Random pages", randomPageIndexes, expectedWeightOf3Probes);

    final int[] randomBlocksOfPageIndexes = IntStream.rangeClosed(0, PAGES_TO_CREATE / 4)
      .flatMap(i -> {
        final int startPageNo = rnd.nextInt(2 * PAGES_TO_CREATE);
        return IntStream.of(startPageNo, startPageNo + 1, startPageNo + 2, startPageNo + 4);
      }).toArray();
    checkProbeLengthsAreShort("Random blocks of 4 pages", randomBlocksOfPageIndexes, expectedWeightOf3Probes);
  }

  private static void checkProbeLengthsAreShort(final String caption,
                                                final int[] pageIndexes,
                                                final double expectedRatioOfFirst3Probes) throws IOException {
    final PagesTable pages = new PagesTable(8);
    for (int pageIndex : pageIndexes) {
      pages.lookupOrCreate(
        pageIndex,
        PagesTableTest::createBlankPage,
        PagesTableTest::allocateAndLoadPage
      );
    }

    final Int2IntMap probeLengthsHisto = pages.collectProbeLengthsHistogram();
    final int totalEntries = probeLengthsHisto.values().intStream().sum();

    final int totalEntriesFoundIn3Probes = IntStream.of(0, 1, 2)
      .map(probeLengthsHisto::get)
      .sum();
    final double totalRatioOfFirst3Probes = totalEntriesFoundIn3Probes * 1.0 / totalEntries;

    final String probesLengthHisto = pages.probeLengthsHistogram();
    System.out.println(caption + ":\n" + probesLengthHisto);
    assertThat(totalRatioOfFirst3Probes)
      .describedAs((expectedRatioOfFirst3Probes * 100) + "% of entries in hash table should be found in (0, 1, 2)-probes")
      .isGreaterThan(expectedRatioOfFirst3Probes);
  }


  //=========== infrastructure:

  private static PageImpl createBlankPage(final int pageIndex) {
    return PageImpl.notReady(
      pageIndex,
      PAGE_SIZE,
      flusher
    );
  }

  private static ByteBuffer allocateAndLoadPage(final @NotNull Page page) {
    return ByteBuffer.allocate(0);
  }

  private static final PageToStorageHandle flusher = new PageToStorageHandle() {
    @Override
    public void pageBecomeDirty() {
      throw new UnsupportedOperationException("Not implemented in this test");
    }

    @Override
    public void pageBecomeClean() {
      throw new UnsupportedOperationException("Not implemented in this test");
    }

    @Override
    public void modifiedRegionUpdated(final long startOffsetInFile,
                                      final int length) {
      throw new UnsupportedOperationException("Not implemented in this test");
    }

    @Override
    public void flushBytes(final @NotNull ByteBuffer dataToFlush,
                           final long offsetInFile) throws IOException {
      throw new UnsupportedOperationException("Flush is not implemented in this test");
    }
  };
}