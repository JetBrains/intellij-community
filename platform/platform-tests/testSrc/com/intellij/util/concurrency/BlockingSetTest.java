package com.intellij.util.concurrency;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Alexander Koshevoy
 */
public class BlockingSetTest {
  @Test
  public void testSingleThreadLock() {
    BlockingSet<String> lock = new BlockingSet<>();
    lock.put("eins");
    lock.put("zwei");
    lock.remove("zwei");
    lock.put("polizei");
    lock.remove("polizei");
    lock.remove("eins");
  }

  @Test(expected = IllegalStateException.class)
  public void testReleaseNotAcquired() {
    BlockingSet<String> lock = new BlockingSet<>();
    lock.put("eins");
    lock.put("zwei");
    lock.remove("eins");
    lock.remove("polizei");
  }

  @Test
  public void testMultipleThreads() throws Exception {
    final BlockingSet<String> lock = new BlockingSet<>();
    int threads = 10;
    int tasks = 10000;
    ExecutorService service = Executors.newFixedThreadPool(threads);
    List<Callable<Void>> taskList = new ArrayList<>(tasks);
    final AtomicBoolean check = new AtomicBoolean(false);
    for (int i = 0; i < tasks; i++) {
      taskList.add(() -> {
        lock.put("key");
        try {
          Assert.assertFalse(check.get());
          check.set(true);
          Thread.sleep(1);
          check.set(false);
        }
        finally {
          lock.remove("key");
        }
        return null;
      });
    }
    List<Future<Void>> futures = service.invokeAll(taskList);
    service.shutdown();
    for (Future<Void> future : futures) {
      future.get();
    }
    Assert.assertTrue(service.awaitTermination(100, TimeUnit.SECONDS));
  }
}