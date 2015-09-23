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

import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Dmitry Avdeev
 */
public class PersistenceStressTest extends TestCase {

  private ExecutorService myThreadPool;
  private PersistentHashMap<String, Record> myMap;
  private PersistentHashMap<String, Record> myMap1;

  public static class Record {
    public final int magnitude;
    public final Date date;

    public Record(int magnitude, Date date) {
      this.magnitude = magnitude;
      this.date = date;
    }
  }


  public void testReadWrite() throws Exception {

    myThreadPool = Executors.newFixedThreadPool(5);

    Future<Boolean> future = submit(myMap);
    Future<Boolean> submit = submit(myMap1);
    while (!future.isDone() || !submit.isDone()) { Thread.sleep(100);}
    assertTrue(future.get());
    assertTrue(submit.get());
  }

  @NotNull
  protected Future<Boolean> submit(final PersistentHashMap<String, Record> map) {
    return myThreadPool.submit(() -> {
      try {
        doTask(map);
        return true;
      }
      catch (IOException e) {
        e.printStackTrace();
        return false;
      }
    });
  }

  protected void doTask(PersistentHashMap<String, Record> map) throws IOException {
    Random random = new Random();
    for (int i = 0; i < 10000; i++) {
      if (random.nextInt(10) == 1) {
        map.force();
        continue;
      }
      byte[] bytes = new byte[10];
      random.nextBytes(bytes);
      String key = random.nextBoolean() ? "a" : "b";
      if (random.nextBoolean()) {
        for (int j = 0; j < 10; j++) {
          map.put(key, new Record(random.nextInt(), new Date()));
        }
      }
      else {
        for (int j = 0; j < 10; j++) {
          map.get(random.nextBoolean() ? key : "");
        }
      }
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myMap = createMap(FileUtil.createTempFile("persistent", "map"));
    myMap1 = createMap(FileUtil.createTempFile("persistent", "map1"));
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

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myMap.close();
    myMap1.close();
    myThreadPool.shutdown();
  }
}
