/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.CacheUpdateRunner;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author Dmitry Avdeev
 */
public class PersistenceStressTest extends LightPlatformCodeInsightFixtureTestCase {
  private static final Condition<Future<Boolean>> STILL_RUNNING = future -> !future.isDone();
  private final ExecutorService myThreadPool = PooledThreadExecutor.INSTANCE;
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
      String key = new String(bytes, CharsetToolkit.UTF8_CHARSET);
      myKeys.add(key);
    }
    File tempDirectory = FileUtil.createTempDirectory("map", "");

    for (int i = 0; i < 10; i++) {
      PersistentHashMap<String, Record> map = createMap(FileUtil.createTempFile(tempDirectory, "persistent", "map" + i));
      myMaps.add(map);
    }
    PagedFileStorage.StorageLockContext storageLockContext = new PagedFileStorage.StorageLockContext(false);
    myEnumerator = new PersistentStringEnumerator(FileUtil.createTempFile(tempDirectory, "persistent", "enum"), storageLockContext);
  }

  @Override
  public void tearDown() throws Exception {
    for (PersistentHashMap<String, Record> map : myMaps) {
      map.close();
    }
    myEnumerator.close();
    myEnumerator = null;
    myMaps.clear();
    super.tearDown();
  }

  public void testReadWrite() throws Exception {
    List<Future<Boolean>> futures = new ArrayList<>();
    for (PersistentHashMap<String, Record> map : myMaps) {
      Future<Boolean> submit = submit(map);
      futures.add(submit);
    }
    Future<?> waitFuture = myThreadPool.submit(() -> {
      try {
        while (ContainerUtil.find(futures, STILL_RUNNING) != null) {
          Thread.sleep(100);
          myMaps.forEach(PersistentHashMap::dropMemoryCaches);
        }
      }
      catch (InterruptedException ignore) {
      }
    });

    List<VirtualFile> files = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      File file = FileUtil.createTempFile("", ".txt");
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      assertNotNull(virtualFile);
      PlatformTestCase.setFileText(virtualFile, "foo bar");
      files.add(virtualFile);
    }

    FileBasedIndexImpl index = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    while (ContainerUtil.find(futures, STILL_RUNNING) != null) {
      Thread.sleep(100);
      CacheUpdateRunner.processFiles(new EmptyProgressIndicator(), files, getProject(),
                                     content -> index.indexFileContent(getProject(), content));
    }
    for (Future<Boolean> future : futures) {
      assertTrue(future.get());
    }
    waitFuture.get();
  }

  @NotNull
  private Future<Boolean> submit(final PersistentHashMap<String, Record> map) {
    return myThreadPool.submit(() -> doTask(map));
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
    return new PersistentHashMap<>(file, new EnumeratorStringDescriptor(), new DataExternalizer<Record>() {
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
