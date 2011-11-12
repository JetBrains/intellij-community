/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.runner.OutputLineSplitter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.concurrency.FutureResult;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class OutputLineSplitterTest extends UsefulTestCase {
  private static final Key RED = Key.create(OutputLineSplitterTest.class + ".RED");
  private static final Key GREEN = Key.create(OutputLineSplitterTest.class + ".GREEN");
  private static final Key BLUE = Key.create(OutputLineSplitterTest.class + ".BLUE");

  private static final List<Key> ALL_TYPES = Arrays.asList(ProcessOutputTypes.STDERR, ProcessOutputTypes.STDOUT, ProcessOutputTypes.SYSTEM);
  private static final List<Key> ALL_COLORS = Arrays.asList(RED, GREEN, BLUE);

  private OutputLineSplitter mySplitter;
  final Map<Key, List<String>> myOutput = new THashMap<Key, List<String>>();


  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySplitter = new OutputLineSplitter(false) {
      @Override
      protected void onLineAvailable(@NotNull String text, @NotNull Key outputType, boolean tcLikeFakeOutput) {
        if (ProcessOutputTypes.STDERR != outputType && ProcessOutputTypes.SYSTEM != outputType) outputType = ProcessOutputTypes.STDOUT;
        synchronized (myOutput) {
          List<String> list = myOutput.get(outputType);
          if (list == null) {
            myOutput.put(outputType, list = new ArrayList<String>());
          }
          list.add(text);
        }
      }
    };
  }

  public void testReadingSeveralStreams() throws Exception {
    final Map<Key, List<String>> written = new ConcurrentHashMap<Key, List<String>>();
    for (final Key each : ALL_TYPES) {
      written.put(each, new ArrayList<String>());
      execute(new Runnable() {
        @Override
        public void run() {
          Random r = new Random();
          for (int i = 0; i < 1000; i++) {
            String s = StringUtil.repeat("A", 100 + r.nextInt(1000));
            if (r.nextInt(1) == 1) s += "\n";

            mySplitter.process(s, each);
            List<String> list = written.get(each);
            if (!list.isEmpty()) {
              String last = list.get(list.size() - 1);
              if (!last.endsWith("\n")) {
                list.set(list.size() - 1, last + s);
                continue;
              }
            }
            list.add(s);
          }
        }
      }).get();
    }

    mySplitter.flush();

    for (Key eachType : ALL_TYPES) {
      assertOrderedEquals(myOutput.get(eachType), written.get(eachType));
    }
  }

  public void testReadingColoredStreams() throws Exception {
    final Map<Key, List<String>> written = new ConcurrentHashMap<Key, List<String>>();
    for (final Key each : ALL_TYPES) {
      written.put(each, new ArrayList<String>());
      execute(new Runnable() {
        @Override
        public void run() {
          Random r = new Random();
          for (int i = 0; i < 1000; i++) {
            String s = StringUtil.repeat("A", 100 + r.nextInt(1000)) + "\n";

            if (each == ProcessOutputTypes.STDOUT) {
              mySplitter.process(s, ALL_COLORS.get(r.nextInt(2)));
            }
            else {
              mySplitter.process(s, each);
            }
            written.get(each).add(s);
          }
        }
      }).get();
    }

    mySplitter.flush();

    for (Key eachType : ALL_TYPES) {
      assertOrderedEquals(myOutput.get(eachType), written.get(eachType));
    }
  }

  public void testFlushing() throws Exception {
    final Semaphore written = new Semaphore(0);
    final Semaphore read = new Semaphore(0);

    final AtomicBoolean isFinished = new AtomicBoolean();
    List<Future<?>> futures = new ArrayList<Future<?>>();

    for (final Key each : ALL_TYPES) {
      futures.add(execute(new Runnable() {
        public void run() {
          int i = 0;
          while (!isFinished.get()) {
            mySplitter.process(StringUtil.repeat("A", 100), each);
            i++;
            if (i % 10 == 0) {
              written.release();
              try {
                if (!read.tryAcquire(1, TimeUnit.SECONDS)) throw new TimeoutException();
              }
              catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          }
        }
      }));
    }

    try {
      boolean hadOutput = false;
      for (int i = 0; i < 100; i++) {
        written.acquire(ALL_TYPES.size());

        mySplitter.flush();

        synchronized (myOutput) {
          for (Key each : ALL_TYPES) {
            List<String> out = myOutput.get(each);
            if (!out.isEmpty()) {
              assertSize(1, out);
              out.clear();
              hadOutput = true;
            }
          }
        }

        read.release(ALL_TYPES.size());
      }
      assertTrue(hadOutput);
    }
    finally {
      isFinished.set(true);

      for (Future<?> each : futures) {
        each.get(1, TimeUnit.SECONDS);
      }
    }
  }

  private Future<?> execute(final Runnable runnable) {
    final FutureResult<?> result = new FutureResult<Object>();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
          result.set(null);
        }
        catch (Throwable e) {
          result.setException(e);
        }
      }
    }).start();
    return result;
  }
}
