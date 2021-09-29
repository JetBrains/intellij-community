// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.SystemProperties;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;


@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class PagePool {
  private final Map<PoolPageKey, Page> myProtectedQueue;
  private final Map<PoolPageKey, Page> myProbationalQueue;

  private int finalizationId = 0;

  private final SortedMap<PoolPageKey, FinalizationRequest> myFinalizationQueue = new TreeMap<>();

  private final Object lock = new Object();

  private PoolPageKey lastFinalizedKey = null;

  public PagePool(final int protectedPagesLimit, final int probationalPagesLimit) {
    myProbationalQueue = new LinkedHashMap<PoolPageKey,Page>(probationalPagesLimit * 2, 1, true) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<PoolPageKey, Page> eldest) {
        if (size() > probationalPagesLimit) {
          scheduleFinalization(eldest.getValue());
          return true;
        }
        return false;
      }
    };

    myProtectedQueue = new LinkedHashMap<PoolPageKey, Page>(protectedPagesLimit, 1, true) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<PoolPageKey, Page> eldest) {
        if (size() > protectedPagesLimit) {
          myProbationalQueue.put(eldest.getKey(), eldest.getValue());
          return true;
        }
        return false;
      }
    };
  }

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private static int hits = 0;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private static int cache_misses = 0;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private static int same_page_hits = 0;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private static int protected_queue_hits = 0;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private static int probational_queue_hits = 0;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private static int finalization_queue_hits = 0;

  public static final PagePool SHARED = new PagePool(
    SystemProperties.getIntProperty("idea.io.protected.pool.size", 256), // 256 * 8 = 2M
    SystemProperties.getIntProperty("idea.io.probatonal.pool.size", 256)
  );

  private RandomAccessDataFile lastOwner = null;
  private long lastOffset = 0;
  private Page lastHit = null;

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
  @NotNull
  public Page alloc(@NotNull RandomAccessDataFile owner, long offset) {
    synchronized (lock) {
      RandomAccessDataFile.ensureNonNegative(offset, "offset");
      offset -= offset % Page.PAGE_SIZE;
      hits++;

      if (owner == lastOwner && offset == lastOffset) {
        same_page_hits++;
        return lastHit;
      }

      lastOffset = offset;
      lastOwner = owner;
      lastHit = hitQueues(owner, offset);

      flushFinalizationQueue();

      return lastHit;
    }
  }

  private Page hitQueues(@NotNull RandomAccessDataFile owner, final long offset) {
    PoolPageKey key = new PoolPageKey(owner, offset);

    Page page = myProtectedQueue.get(key);
    if (page != null) {
      protected_queue_hits++;
      return page;
    }

    page = myProbationalQueue.remove(key);
    if (page != null) {
      probational_queue_hits++;
      toProtectedQueue(page);
      return page;
    }

    final FinalizationRequest request = myFinalizationQueue.remove(key);
    if (request != null) {
      page = request.page;
      finalization_queue_hits++;
      toProtectedQueue(page);
      return page;
    }

    cache_misses++;
    page = new Page(new PoolPageKey(owner, offset));

    myProbationalQueue.put(page.getKey(), page);

    return page;
  }

  //private long lastFlushTime = 0;

  private static double percent(int part, int whole) {
    return ((double)part * 1000 / whole) / 10;
  }

  @SuppressWarnings({"ALL"})
  public static void printStatistics() {
    System.out.println("Total requests: " + hits);
    System.out.println("Same page hits: " + same_page_hits + " (" + percent(same_page_hits, hits) + "%)");
    System.out.println("Protected queue hits: " + protected_queue_hits + " (" + percent(protected_queue_hits, hits) + "%)");
    System.out.println("Probatinonal queue hits: " + probational_queue_hits + " (" + percent(probational_queue_hits, hits) + "%)");
    System.out.println("Finalization queue hits: " + finalization_queue_hits + " (" + percent(finalization_queue_hits, hits) + "%)");
    System.out.println("Cache misses: " + cache_misses + " (" + percent(cache_misses, hits) + "%)");

    System.out.println("Total reads: " + RandomAccessDataFile.totalReads + ". Bytes read: " + RandomAccessDataFile.totalReadBytes);
    System.out.println("Total writes: " + RandomAccessDataFile.totalWrites + ". Bytes written: " + RandomAccessDataFile.totalWriteBytes);
  }

  private void toProtectedQueue(final Page page) {
    myProtectedQueue.put(page.getKey(), page);
  }

  public void flushPages(final RandomAccessDataFile owner) {
    boolean hasFlushes;
    synchronized (lock) {
      if (lastOwner == owner) {
        scheduleFinalization(lastHit);
        lastHit = null;
        lastOwner = null;
      }

      hasFlushes = scanQueue(owner, myProtectedQueue);
      hasFlushes |= scanQueue(owner, myProbationalQueue);
    }

    if (hasFlushes) {
      flushFinalizationQueue();
    }
  }

  private void flushFinalizationQueue() {
    while (true) {
      FinalizationRequest request = retrieveFinalizationRequest();
      if (request == null) {
        return;
      }

      processFinalizationRequest(request);
    }
  }

  private boolean scanQueue(final RandomAccessDataFile owner, final Map<?, Page> queue) {
    Iterator<Page> iterator = queue.values().iterator();
    boolean hasFlushes = false;
    while (iterator.hasNext()) {
      Page page = iterator.next();

      if (page.getOwner() == owner) {
        scheduleFinalization(page);
        iterator.remove();
        hasFlushes = true;
      }
    }
    return hasFlushes;
  }

  private void scheduleFinalization(final Page page) {
    final int curFinalizationId;
    synchronized (lock) {
      curFinalizationId = ++finalizationId;
    }

    final FinalizationRequest request = page.prepareForFinalization(curFinalizationId);
    if (request == null) return;

    synchronized (lock) {
      myFinalizationQueue.put(page.getKey(), request);
    }
  }

  private void processFinalizationRequest(final FinalizationRequest request) {
    final Page page = request.page;
    try {
      page.flushIfFinalizationIdIsEqualTo(request.finalizationId);
    }
    finally {
      synchronized (lock) {
        myFinalizationQueue.remove(page.getKey());
      }
      page.recycleIfFinalizationIdIsEqualTo(request.finalizationId);
    }
  }

  @Nullable
  private FinalizationRequest retrieveFinalizationRequest() {
    FinalizationRequest request = null;
    synchronized (lock) {
      if (!myFinalizationQueue.isEmpty()) {
        final PoolPageKey key;
        if (lastFinalizedKey == null) {
          key = myFinalizationQueue.firstKey();
        }
        else {
          PoolPageKey k = lastFinalizedKey;
          PoolPageKey kk = new PoolPageKey(k.getOwner(), k.getOwner().physicalLength());

          SortedMap<PoolPageKey, FinalizationRequest> tail = myFinalizationQueue.tailMap(kk);
          if (tail.isEmpty()) {
            tail = myFinalizationQueue.tailMap(k);
          }
          key = tail.isEmpty() ? myFinalizationQueue.firstKey() : tail.firstKey();
        }
        lastFinalizedKey = key;
        request = myFinalizationQueue.get(key);
      }
      else {
        lastFinalizedKey = null;
      }
    }
    return request;
  }
}
