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
package com.intellij.execution.testframework.sm;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.runner.OutputLineSplitter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.*;

public class OutputLineSplitterTest extends PlatformTestCase {
  private static final List<Key> ALL_TYPES = Arrays.asList(ProcessOutputTypes.STDERR, ProcessOutputTypes.STDOUT, ProcessOutputTypes.SYSTEM);
  private static final List<Key> ALL_STDOUT_KEYS = Arrays.asList(
    new ProcessOutputType(OutputLineSplitterTest.class + ".RED", (ProcessOutputType)ProcessOutputTypes.STDOUT),
    new ProcessOutputType(OutputLineSplitterTest.class + ".GREEN", (ProcessOutputType)ProcessOutputTypes.STDOUT),
    new ProcessOutputType(OutputLineSplitterTest.class + ".BLUE", (ProcessOutputType)ProcessOutputTypes.STDOUT)
  );
  private static final List<Key> ALL_STDERR_KEYS = Arrays.asList(
    new ProcessOutputType(OutputLineSplitterTest.class + ".RED", (ProcessOutputType)ProcessOutputTypes.STDERR),
    new ProcessOutputType(OutputLineSplitterTest.class + ".GREEN", (ProcessOutputType)ProcessOutputTypes.STDERR),
    new ProcessOutputType(OutputLineSplitterTest.class + ".BLUE", (ProcessOutputType)ProcessOutputTypes.STDERR)
  );

  private OutputLineSplitter mySplitter;
  final Map<Key, List<String>> myOutput = new THashMap<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySplitter = new OutputLineSplitter(false) {
      @Override
      protected void onLineAvailable(@NotNull String text, @NotNull Key outputType, boolean tcLikeFakeOutput) {
        ProcessOutputType baseOutputType = ((ProcessOutputType)outputType).getBaseOutputType();
        synchronized (myOutput) {
          List<String> list = myOutput.get(baseOutputType);
          if (list == null) {
            myOutput.put(baseOutputType, list = new ArrayList<>());
          }
          list.add(text);
        }
      }
    };
  }

  /**
   * When tc message is in the middle of line it should reported as separate line like if it has \n before it
   */
  public void testMessageInTheMiddleOfLine() {
    mySplitter.process("\nStarting...\n##teamcity[name1]\nDone 1\n\n##teamcity[name2]\nDone 2", ProcessOutputTypes.STDOUT);
    mySplitter.process("##teamcity[name3]Test print##teamcity[name4]", ProcessOutputTypes.STDOUT);
    mySplitter.process("##teamcity[name5]\n", ProcessOutputTypes.STDOUT);
    mySplitter.process("##teamcity[name6]\nInfo##teamcity[name7]\n", ProcessOutputTypes.STDOUT);
    List<String> stdout = myOutput.get(ProcessOutputTypes.STDOUT);
    Assert.assertEquals(ContainerUtil.newArrayList(
      "\n", "Starting...\n", "##teamcity[name1]\n", "Done 1\n", "\n", "##teamcity[name2]\n", "Done 2",
      "##teamcity[name3]Test print", "##teamcity[name4]",
      "##teamcity[name5]\n",
      "##teamcity[name6]\n", "Info", "##teamcity[name7]\n"
    ), stdout);
    for(String prefix: new String[]{"...", "", "... ", "##", " ##", "##team##teamcity["}) {
      final String testStarted = ServiceMessageBuilder.testStarted("myTest").toString() + "\n";
      final String testEnded = ServiceMessageBuilder.testFinished("myTest").toString() + "\n";

      mySplitter.process(prefix, ProcessOutputTypes.SYSTEM);
      mySplitter.process(prefix + testStarted, ProcessOutputTypes.SYSTEM);
      mySplitter.process(testEnded, ProcessOutputTypes.SYSTEM);

      mySplitter.flush();

      final List<String> output = myOutput.get(ProcessOutputTypes.SYSTEM);
      final String messagePrefix = ServiceMessage.SERVICE_MESSAGE_START;
      Assert.assertThat(output, everyItem(either(startsWith(messagePrefix)).or(not(containsString(messagePrefix)))));
      Assert.assertThat(output, hasItems(testStarted, testEnded));
    }
  }

  public void testReadingSeveralStreams() throws Exception {
    final Map<Key, List<String>> written = new ConcurrentHashMap<>();
    for (final Key each : ALL_TYPES) {
      written.put(each, new ArrayList<>());
      execute(() -> {
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
      }).get();
    }

    mySplitter.flush();

    for (Key eachType : ALL_TYPES) {
      assertOrderedEquals(myOutput.get(eachType), written.get(eachType));
    }
  }

  public void testReadingColoredStreams() throws Exception {
    final Map<Key, List<String>> written = new ConcurrentHashMap<>();
    for (final Key each : ALL_TYPES) {
      written.put(each, new ArrayList<>());
      execute(() -> {
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
          String s = StringUtil.repeat("A", 100 + r.nextInt(1000)) + "\n";

          final Key outputType;
          if (each == ProcessOutputTypes.STDOUT) {
            outputType = ALL_STDOUT_KEYS.get(r.nextInt(2));
          }
          else if (each == ProcessOutputTypes.STDERR) {
            outputType = ALL_STDERR_KEYS.get(r.nextInt(2));
          }
          else {
            outputType = each;
          }
          String prefix = each.toString() + ":";
          mySplitter.process(prefix, each);
          mySplitter.process(s, outputType);
          if (!outputType.equals(each)) {
            written.get(each).add(prefix);
            written.get(each).add(s);
          }
          else {
            written.get(each).add(prefix + s);
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
    List<Future<?>> futures = new ArrayList<>();

    for (final Key each : ALL_TYPES) {
      futures.add(execute(() -> {
        int i = 0;
        while (!isFinished.get()) {
          mySplitter.process(StringUtil.repeat("A", 100), each);
          i++;
          if (i % 10 == 0) {
            written.release();
            try {
              if (!read.tryAcquire(10, TimeUnit.SECONDS)) throw new TimeoutException();
            }
            catch (Exception e) {
              throw new RuntimeException(e);
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
      try {
        isFinished.set(true);

        for (Future<?> each : futures) {
          each.get();
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void testPerformanceWithLotsOfFragments() {
    PlatformTestUtil.startPerformanceTest("Flushing lot's of fragments", 5, mySplitter::flush)
      .setup(() -> {
        for (int i = 0; i < 10_000; i++) {
          mySplitter.process("some string without slash n appending in raw, attempt: " + i + "; ", ProcessOutputTypes.STDOUT);
        }
      })
      .useLegacyScaling()
      .assertTiming();
  }

  private static Future<?> execute(final Runnable runnable) {
    return ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }
}
