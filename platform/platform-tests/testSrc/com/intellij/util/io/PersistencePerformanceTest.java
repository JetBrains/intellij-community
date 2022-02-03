// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Future;

/**
 * @author Dmitry Avdeev
 */
public class PersistencePerformanceTest extends BasePlatformTestCase {
  private final List<PersistentHashMap<String, Record>> myMaps = new ArrayList<>();
  private final List<String> myKeys = new ArrayList<>();
  private PersistentStringEnumerator myEnumerator;

  public static class Record {
    final int magnitude;
    public final Date date;

    Record(int magnitude, Date date) {
      this.magnitude = magnitude;
      this.date = date;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Random random = new Random();
    for (int i = 0; i < 100; i++) {
      byte[] bytes = new byte[1000];
      random.nextBytes(bytes);
      String key = new String(bytes, StandardCharsets.UTF_8);
      myKeys.add(key);
    }
    File tempDirectory = FileUtil.createTempDirectory("map", "");

    for (int i = 0; i < 10; i++) {
      PersistentHashMap<String, Record> map = createMap(FileUtil.createTempFile(tempDirectory, "persistent", "map" + i));
      myMaps.add(map);
    }
    StorageLockContext storageLockContext = new StorageLockContext(false);
    myEnumerator = new PersistentStringEnumerator(FileUtil.createTempFile(tempDirectory, "persistent", "enum").toPath(), storageLockContext);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      for (PersistentHashMap<String, Record> map : myMaps) {
        map.close();
      }
      myEnumerator.close();
      myEnumerator = null;
      myMaps.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testReadWrite() throws Exception {
    List<Future<Boolean>> futures = Collections.synchronizedList(new ArrayList<>());
    for (PersistentHashMap<String, Record> map : myMaps) {
      Future<Boolean> submit = ApplicationManager.getApplication().executeOnPooledThread(() -> doTask(map));
      futures.add(submit);
    }
    Future<?> waitFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      while (ContainerUtil.exists(futures, future -> !future.isDone())) {
        TimeoutUtil.sleep(100);
        myMaps.forEach(PersistentHashMap::dropMemoryCaches);
      }
    });

    List<VirtualFile> files = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      File file = FileUtil.createTempFile("", ".txt");
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      assertNotNull(virtualFile);
      HeavyPlatformTestCase.setFileText(virtualFile, "foo bar");
      files.add(virtualFile);
    }

    FileBasedIndexImpl index = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    while (ContainerUtil.exists(futures, future -> !future.isDone())) {
      Thread.sleep(100);
      new IndexUpdateRunner(index, UnindexedFilesUpdater.GLOBAL_INDEXING_EXECUTOR, UnindexedFilesUpdater.getNumberOfIndexingThreads())
        .indexFiles(getProject(), Collections.singletonList(new IndexUpdateRunner.FileSet(getProject(), "test files", files)),
                    new EmptyProgressIndicator(), new ProjectIndexingHistoryImpl(getProject(), "Testing", false));
    }
    for (Future<Boolean> future : futures) {
      assertTrue(future.get());
    }
    waitFuture.get();
  }

  private boolean doTask(PersistentHashMap<String, Record> map) {
    Random random = new Random();
    try {
      for (int i = 0; i < 100000; i++) {
        if (random.nextInt(1000) == 1) {
          map.force();
          continue;
        }
        String key = myKeys.get(random.nextInt(myKeys.size()));
        myEnumerator.enumerate(key);
        if (random.nextBoolean()) {
  //        for (int j = 0; j < 10; j++) {
            map.put(key, new Record(random.nextInt(), new Date()));
  //        }
        }
        else {
  //        for (int j = 0; j < 10; j++) {
            map.get(key);
  //        }
        }
      }
//      System.out.println("Done!");
      return true;
    }
    catch (Throwable e) {
      e.printStackTrace();
      return false;
    }
  }

  @NotNull
  private static PersistentHashMap<String, Record> createMap(File file) throws IOException {
    return new PersistentHashMap<>(file, new EnumeratorStringDescriptor(), new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, Record value) throws IOException {
        out.writeInt(value.magnitude);
        out.writeLong(value.date.getTime());
      }

      @Override
      public Record read(@NotNull DataInput in) throws IOException {
        return new Record(in.readInt(), new Date(in.readLong()));
      }
    });
  }
}
