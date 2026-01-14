// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@SkipSlowTestLocally
public class PersistentBTreeEnumeratorPerformanceTest {
  private static final Logger LOG = Logger.getInstance(PersistentBTreeEnumeratorPerformanceTest.class);
  private static final Random random = new Random(13101977);
  
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();
  
  private TestStringEnumerator enumerator;
  private File file;
  
  static class TestStringEnumerator extends PersistentBTreeEnumerator<String> {
    TestStringEnumerator(File file) throws IOException {
      super(file.toPath(), new EnumeratorStringDescriptor(), 4096);
    }
  }
  
  @Before
  public void setUp() throws IOException {
    file = tempDir.newFile("persistent-enumerator");
    enumerator = new TestStringEnumerator(file);
  }
  
  @After
  public void tearDown() throws IOException {
    enumerator.close();
    IOUtil.deleteAllFilesStartingWith(file);
    assertFalse(file.exists());
  }
  
  private static String createRandomString() {
    StringBuilder builder = new StringBuilder(100);
    int len = random.nextInt(40) + 10;
    for (int i = 0; i < len; ++i) {
      builder.append((char)(32 + random.nextInt(2 + i >> 1)));
    }
    return builder.toString();
  }
  
  @Test
  public void testEnumeratePerformance() {
    final int count = 1_000_000;
    final List<String> strings = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      strings.add(createRandomString());
    }
    
    Benchmark.newBenchmark("PersistentBTreeEnumerator enumerate (1M strings)", () -> {
      for (String str : strings) {
        enumerator.enumerate(str);
      }
    }).warmupIterations(0).attempts(1).runAsStressTest().start();
    
    LOG.debug(String.format("Main storage size = %d bytes", file.length()));
    File collisionFile = new File(file.getParentFile(), file.getName() + "_i");
    if (collisionFile.exists()) {
      LOG.debug(String.format("Collision storage size = %d bytes", collisionFile.length()));
    }
  }
  
  @Test
  public void testTryEnumerateExistingPerformance() throws IOException {
    final int count = 1_000_000;
    final List<String> strings = new ArrayList<>(count);
    
    // Setup: enumerate strings first
    for (int i = 0; i < count; i++) {
      String str = createRandomString();
      strings.add(str);
      enumerator.enumerate(str);
    }
    
    Benchmark.newBenchmark("PersistentBTreeEnumerator tryEnumerate existing (1M lookups)", () -> {
      for (String str : strings) {
        int id = enumerator.tryEnumerate(str);
        assertNotEquals(DataEnumerator.NULL_ID, id);
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
  }
  
  @Test
  public void testTryEnumerateNonExistingPerformance() throws IOException {
    final int count = 1_000_000;
    final List<String> nonExistingStrings = new ArrayList<>(count);
    
    // Setup: enumerate some strings
    for (int i = 0; i < count / 10; i++) {
      enumerator.enumerate(createRandomString());
    }
    
    // Generate different strings for lookup
    for (int i = 0; i < count; i++) {
      nonExistingStrings.add("NON_EXISTING_" + i);
    }
    
    Benchmark.newBenchmark("PersistentBTreeEnumerator tryEnumerate non-existing (1M lookups)", () -> {
      for (String str : nonExistingStrings) {
        int id = enumerator.tryEnumerate(str);
        assertEquals(DataEnumerator.NULL_ID, id);
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
  }
  
  @Test
  public void testValueOfPerformance() throws IOException {
    final int count = 1_000_000;
    final List<Integer> ids = new ArrayList<>(count);
    final Map<Integer, String> idToString = new HashMap<>();
    
    // Setup: enumerate strings and collect IDs
    for (int i = 0; i < count; i++) {
      String str = createRandomString();
      int id = enumerator.enumerate(str);
      ids.add(id);
      idToString.put(id, str);
    }
    
    Benchmark.newBenchmark("PersistentBTreeEnumerator valueOf (1M lookups)", () -> {
      for (int id : ids) {
        String value = enumerator.valueOf(id);
        assertNotNull(value);
        assertEquals(idToString.get(id), value);
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
  }
  
  @Test
  public void testCollisionHandlingPerformance() throws IOException {
    final int count = 1_000_000;
    final int collisionRatio = 10; // 10% collisions
    
    // Create strings with some collisions
    final List<String> strings = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      if (i % collisionRatio == 0) {
        // Use a string that may collide (alternating empty string and null char)
        strings.add((i / collisionRatio) % 2 == 0 ? "" : "\u0000");
      } else {
        strings.add(createRandomString());
      }
    }
    
    // Baseline: no collisions
    long baselineStart = System.currentTimeMillis();
    for (int i = 0; i < count / 10; i++) {
      enumerator.enumerate(createRandomString());
    }
    long baselineDuration = System.currentTimeMillis() - baselineStart;
    
    // Reset
    enumerator.close();
    IOUtil.deleteAllFilesStartingWith(file);
    file = tempDir.newFile("persistent-enumerator-collision");
    enumerator = new TestStringEnumerator(file);
    
    // With collisions
    Benchmark.newBenchmark("PersistentBTreeEnumerator enumerate with collisions (1M, 10% collision rate)", () -> {
      for (String str : strings) {
        enumerator.enumerate(str);
      }
    }).warmupIterations(0).attempts(1).runAsStressTest().start();
    
    LOG.debug(String.format("Baseline (no collision): ~%d ms for %d enumerations", baselineDuration, count / 10));
    
    File collisionFile = new File(file.getParentFile(), file.getName() + "_i");
    if (collisionFile.exists()) {
      LOG.debug(String.format("Collision storage created, size = %d bytes", collisionFile.length()));
    }
  }
  
  @Test
  public void testReenumeratePerformance() throws IOException {
    final int count = 1_000_000;
    final List<String> strings = new ArrayList<>(count);
    
    // First enumeration
    for (int i = 0; i < count; i++) {
      String str = createRandomString();
      strings.add(str);
      enumerator.enumerate(str);
    }
    
    // Re-enumerate (should be faster due to fast path)
    Benchmark.newBenchmark("PersistentBTreeEnumerator re-enumerate (1M strings)", () -> {
      for (String str : strings) {
        enumerator.enumerate(str);
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
  }
  
  @Test
  public void testGetAllDataObjectsPerformance() throws IOException {
    final int[] counts = {100_000, 500_000, 1_000_000};
    
    for (int count : counts) {
      // Fresh enumerator for each size
      enumerator.close();
      IOUtil.deleteAllFilesStartingWith(file);
      file = tempDir.newFile("persistent-enumerator-" + count);
      enumerator = new TestStringEnumerator(file);
      
      // Populate with unique strings (use index to guarantee uniqueness)
      for (int i = 0; i < count; i++) {
        enumerator.enumerate("unique_" + i + "_" + createRandomString());
      }
      
      Benchmark.newBenchmark("PersistentBTreeEnumerator getAllDataObjects (" + count + " entries)", () -> {
        Collection<String> allData = enumerator.getAllDataObjects(null);
        assertEquals(count, allData.size());
      }).warmupIterations(1).attempts(2).runAsStressTest().start();
    }
  }
  
  @Test
  public void testPersistenceCyclePerformance() throws IOException {
    final int count = 100_000;
    final List<String> strings = new ArrayList<>(count);
    final List<Integer> ids = new ArrayList<>(count);
    
    // Populate
    for (int i = 0; i < count; i++) {
      String str = createRandomString();
      strings.add(str);
      ids.add(enumerator.enumerate(str));
    }
    
    // Measure flush
    Benchmark.newBenchmark("PersistentBTreeEnumerator flush (100k entries)", () -> {
      enumerator.force();
    }).warmupIterations(1).attempts(3).runAsStressTest().start();
    
    enumerator.close();
    
    // Measure reopen
    Benchmark.newBenchmark("PersistentBTreeEnumerator reopen (100k entries)", () -> {
      try {
        enumerator = new TestStringEnumerator(file);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).warmupIterations(0).attempts(1).runAsStressTest().start();
    
    // Verify data
    for (int i = 0; i < count; i++) {
      assertEquals(ids.get(i).intValue(), enumerator.tryEnumerate(strings.get(i)));
      assertEquals(strings.get(i), enumerator.valueOf(ids.get(i)));
    }
  }
  
  @Test
  public void testMixedWorkloadPerformance() throws IOException {
    final int count = 1_000_000;
    final List<String> existingStrings = new ArrayList<>(count / 2);
    final List<Integer> existingIds = new ArrayList<>(count / 2);
    
    // Setup: populate with half the data
    for (int i = 0; i < count / 2; i++) {
      String str = createRandomString();
      existingStrings.add(str);
      existingIds.add(enumerator.enumerate(str));
    }
    
    final Random workloadRandom = new Random(42);
    final List<String> newStrings = new ArrayList<>(count * 3 / 10);
    for (int i = 0; i < count * 3 / 10; i++) {
      newStrings.add(createRandomString());
    }
    
    Benchmark.newBenchmark("PersistentBTreeEnumerator mixed workload (30% enumerate, 50% tryEnumerate, 20% valueOf)", () -> {
      for (int i = 0; i < count; i++) {
        int operation = workloadRandom.nextInt(100);
        
        if (operation < 30) {
          // 30% enumerate
          enumerator.enumerate(newStrings.get(i % newStrings.size()));
        } else if (operation < 80) {
          // 50% tryEnumerate
          enumerator.tryEnumerate(existingStrings.get(i % existingStrings.size()));
        } else {
          // 20% valueOf
          enumerator.valueOf(existingIds.get(i % existingIds.size()));
        }
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
  }
  
  @Test
  public void testConcurrentEnumeration() throws Exception {
    final int threadsCount = 4;
    final int stringsPerThread = 250_000; // 1M total
    
    ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
    
    try {
      List<Future<?>> futures = new ArrayList<>();
      
      Benchmark.newBenchmark("PersistentBTreeEnumerator concurrent enumeration (4 threads, 1M ops)", () -> {
        futures.clear();
        
        for (int t = 0; t < threadsCount; t++) {
          final int threadId = t;
          futures.add(executor.submit(() -> {
            Random threadRandom = new Random(threadId);
            
            for (int i = 0; i < stringsPerThread; i++) {
              String str = "thread" + threadId + "_" + i + "_" + threadRandom.nextInt(1000);
              
              try {
                if (threadRandom.nextInt(10) < 7) {
                  // 70% enumerate
                  enumerator.enumerate(str);
                } else {
                  // 30% tryEnumerate
                  enumerator.tryEnumerate(str);
                }
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          }));
        }
        
        // Wait for all threads
        for (Future<?> future : futures) {
          try {
            future.get();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }).warmupIterations(1).attempts(2).runAsStressTest().start();
      
    } finally {
      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.MINUTES);
    }
  }
}
