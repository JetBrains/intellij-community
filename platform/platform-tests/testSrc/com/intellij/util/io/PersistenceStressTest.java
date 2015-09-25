/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Dmitry Avdeev
 */
public class PersistenceStressTest extends TestCase {

  public static final Condition<Future<Boolean>> CONDITION = new Condition<Future<Boolean>>() {
    @Override
    public boolean value(Future<Boolean> future) {
      return !future.isDone();
    }
  };
  private ExecutorService myThreadPool;
  private final List<PersistentHashMap<String, Record>> myMaps = new ArrayList<PersistentHashMap<String, Record>>();
  private final List<String> myKeys = new ArrayList<String>();
  private PersistentStringEnumerator myEnumerator;

  public static class Record {
    public final int magnitude;
    public final Date date;

    public Record(int magnitude, Date date) {
      this.magnitude = magnitude;
      this.date = date;
    }
  }

  public void testReadWrite() throws Exception {

    List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
    for (PersistentHashMap<String, Record> map : myMaps) {
      Future<Boolean> submit = submit(map);
      futures.add(submit);
    }
    myThreadPool.submit(new Runnable() {
      @Override
      public void run() {
        try {
          while (ContainerUtil.find(futures, CONDITION) != null) {
            Thread.sleep(100);
            for (PersistentHashMap<String, Record> map : myMaps) {
              map.dropMemoryCaches();
            }
          }
        }
        catch (InterruptedException ignore) {
        }
      }
    });
    while (ContainerUtil.find(futures, CONDITION) != null) {
      Thread.sleep(100);
//      System.out.println("Waiting... ");
    }
  }

  @NotNull
  protected Future<Boolean> submit(final PersistentHashMap<String, Record> map) {
    return myThreadPool.submit(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        try {
          doTask(map);
          return true;
        }
        catch (IOException e) {
          e.printStackTrace();
          return false;
        }
      }
    });
  }

  protected void doTask(PersistentHashMap<String, Record> map) throws IOException {
    Random random = new Random();
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
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Random random = new Random();
    for (int i = 0; i < 1000; i++) {
      byte[] bytes = new byte[1000];
      random.nextBytes(bytes);
      String key = new String(bytes, CharsetToolkit.UTF8_CHARSET);
      myKeys.add(key);
    }
    for (int i = 0; i < 10; i++) {
      PersistentHashMap<String, Record> map = createMap(FileUtil.createTempFile("persistent", "map" + i));
      myMaps.add(map);
    }
    PagedFileStorage.StorageLockContext storageLockContext = new PagedFileStorage.StorageLockContext(false);
    myEnumerator = new PersistentStringEnumerator(FileUtil.createTempFile("persistent", "enum"), storageLockContext);

    myThreadPool = Executors.newFixedThreadPool(myMaps.size() + 1);
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    for (PersistentHashMap<String, Record> map : myMaps) {
      map.close();
    }
    myEnumerator.close();
    myThreadPool.shutdown();
  }

  @NotNull
  private static PersistentHashMap<String, Record> createMap(File file) throws IOException {
    return new PersistentHashMap<String, Record>(file, new EnumeratorStringDescriptor(), new DataExternalizer<Record>() {
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
