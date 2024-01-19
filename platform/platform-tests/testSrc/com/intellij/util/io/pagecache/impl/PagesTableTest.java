// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.io.pagecache.Page;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(Parameterized.class)
public class PagesTableTest {

  private static final int PAGE_SIZE = 4096;
  private static final int PAGES_TO_CREATE = 8 * 1024;

  private final IntFunction<PageImpl> pagesFactory;

  @Parameterized.Parameters(name = "{index}: {0}")
  public static List<IntFunction<PageImpl>> pagesImplToCheck() {
    return List.of(
      new IntFunction<>() {
        @Override
        public PageImpl apply(int pageIndex) {
          return new PageImplForTests(pageIndex);
        }

        @Override
        public String toString() {
          return "PageImplForTests";
        }
      },
      new IntFunction<>() {
        @Override
        public PageImpl apply(int index) {
          return RWLockProtectedPageImpl.createBlankWithOwnLock(index, PAGE_SIZE, FLUSHER);
        }

        @Override
        public String toString() {
          return "RWLockProtectedPageImpl";
        }
      }
    );
  }


  public PagesTableTest(final IntFunction<PageImpl> pagesFactory) {
    this.pagesFactory = pagesFactory;
  }

  @Test
  public void tableCreatesBlankPagesPerRequest() throws IOException {
    final PagesTable pages = new PagesTable(8);
    final int pagesToCreate = 8 * 1024;
    for (int pageIndex = 0; pageIndex < pagesToCreate; pageIndex++) {
      final PageImpl page = pages.lookupOrCreate(
        pageIndex,
        pagesFactory
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

      assertThat(page.isNotReadyYet())
        .describedAs("Page must be NOT_READY_YET: " + page)
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
        pagesFactory
      );
      pagesCreated[pageIndex] = page;
    }

    for (int pageIndex = 0; pageIndex < PAGES_TO_CREATE; pageIndex++) {
      final Page page = pages.lookupOrCreate(
        pageIndex,
        index -> fail("Should not create new page " + index + " -> should return already created one")
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
                  pagesFactory
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
        pagesFactory
      );
      //entomb the page:
      page.tryMoveTowardsPreTombstone(true);
      page.entomb();

      assertThat(pages.lookupIfExist(pageIndex))
        .describedAs("(PRE_)TOMBSTONEs are ignored for lookups")
        .isNull();

      final PageImpl pageForSameIndex = pages.lookupOrCreate(
        pageIndex,
        pagesFactory
      );
      assertThat(pageForSameIndex)
        .describedAs("(PRE_)TOMBSTONEs are overwritten with new pages during .lookupOrCreate()")
        .isNotEqualTo(page);
      assertThat(pageForSameIndex.isNotReadyYet())
        .describedAs("New pages are blank (NOT_READY_YET)")
        .isTrue();
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
    checkProbeLengthsAreShort("Probe length histogram for sequential pages", sequentialPageIndexes, expectedWeightOf3Probes);

    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final int[] randomPageIndexes = IntStream.rangeClosed(0, PAGES_TO_CREATE)
      .map(i -> rnd.nextInt(2 * PAGES_TO_CREATE))
      .toArray();
    checkProbeLengthsAreShort("Probe length histogram for random pages", randomPageIndexes, expectedWeightOf3Probes);

    final int[] randomBlocksOfPageIndexes = IntStream.rangeClosed(0, PAGES_TO_CREATE / 4)
      .flatMap(i -> {
        final int startPageNo = rnd.nextInt(2 * PAGES_TO_CREATE);
        return IntStream.of(startPageNo, startPageNo + 1, startPageNo + 2, startPageNo + 4);
      }).toArray();
    checkProbeLengthsAreShort("Probe length histogram for random blocks of 4 pages", randomBlocksOfPageIndexes, expectedWeightOf3Probes);
  }

  private void checkProbeLengthsAreShort(final String caption,
                                         final int[] pageIndexes,
                                         final double expectedRatioOfFirst3Probes) throws IOException {
    final PagesTable pages = new PagesTable(8);
    for (int pageIndex : pageIndexes) {
      pages.lookupOrCreate(
        pageIndex,
        pagesFactory
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

  private static PageImpl createRWLockProtectedPageImpl(int index) {
    return RWLockProtectedPageImpl.createBlankWithOwnLock(index, PAGE_SIZE, FLUSHER);
  }

  private static final PageToStorageHandle FLUSHER = new PageToStorageHandle() {
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

  private static class PageImplForTests extends PageImpl {
    protected PageImplForTests(final int pageIndex) { super(pageIndex, PAGE_SIZE, FLUSHER); }

    @Override
    public void lockPageForWrite() {
      throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }

    @Override
    public void unlockPageForWrite() {
      throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }

    @Override
    public void lockPageForRead() {
      throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }

    @Override
    public void unlockPageForRead() {
      throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }

    @Override
    public boolean tryFlush() throws IOException {
      //throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
      return false;
    }

    @Override
    public boolean isDirty() {
      return false;
      //throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }

    @Override
    public void flush() throws IOException {
      //throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }

    @Override
    public <OUT, E extends Exception> OUT read(final int startOffsetOnPage,
                                               final int length,
                                               final ThrowableNotNullFunction<ByteBuffer, OUT, E> reader) throws E {
      throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }

    @Override
    public <OUT, E extends Exception> OUT write(final int startOffsetOnPage,
                                                final int length,
                                                final ThrowableNotNullFunction<ByteBuffer, OUT, E> writer) throws E {
      throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }

    @Override
    public void regionModified(final int startOffsetModified, final int length) {
      throw new UnsupportedOperationException("Method is INTENTIONALLY not implemented");
    }
  }
}