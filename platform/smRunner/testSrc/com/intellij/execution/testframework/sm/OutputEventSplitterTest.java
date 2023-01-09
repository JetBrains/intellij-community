// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm;

import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.runner.OutputEventSplitter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.hamcrest.core.IsCollectionContaining;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.*;

public class OutputEventSplitterTest extends LightPlatformTestCase {
  private static final List<ProcessOutputType> ALL_TYPES = Arrays.asList(ProcessOutputType.STDERR, ProcessOutputType.STDOUT, ProcessOutputType.SYSTEM);
  private static final List<Key> ALL_STDOUT_KEYS = Arrays.asList(
    new ProcessOutputType(OutputEventSplitterTest.class + ".RED", (ProcessOutputType)ProcessOutputTypes.STDOUT),
    new ProcessOutputType(OutputEventSplitterTest.class + ".GREEN", (ProcessOutputType)ProcessOutputTypes.STDOUT),
    new ProcessOutputType(OutputEventSplitterTest.class + ".BLUE", (ProcessOutputType)ProcessOutputTypes.STDOUT)
  );
  private static final List<Key> ALL_STDERR_KEYS = Arrays.asList(
    new ProcessOutputType(OutputEventSplitterTest.class + ".RED", (ProcessOutputType)ProcessOutputTypes.STDERR),
    new ProcessOutputType(OutputEventSplitterTest.class + ".GREEN", (ProcessOutputType)ProcessOutputTypes.STDERR),
    new ProcessOutputType(OutputEventSplitterTest.class + ".BLUE", (ProcessOutputType)ProcessOutputTypes.STDERR)
  );

  private OutputEventSplitter mySplitter;
  private final Map<ProcessOutputType, Console> myOutput = new HashMap<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySplitter = createEventSplitter(false, false);
  }

  @NotNull
  private OutputEventSplitter createEventSplitter(final boolean bufferTextUntilNewLine, final boolean cutNewLineBeforeServiceMessage) {
    return new OutputEventSplitter(bufferTextUntilNewLine, cutNewLineBeforeServiceMessage) {
      @Override
      public void onTextAvailable(@NotNull final String text, @NotNull final Key<?> outputType) {
        final ProcessOutputType baseOutputType = ((ProcessOutputType)outputType).getBaseOutputType();
        synchronized (myOutput) {
          final Console console = myOutput.computeIfAbsent(baseOutputType, key -> new Console());
          console.processText(text, outputType);
        }
      }
    };
  }

  public void testLongText() {
    final int maxSize = ConsoleBuffer.getCycleBufferSize();
    final String string = "abc";
    final String longString = StringUtil.repeat(string, maxSize);
    mySplitter.process(longString, ProcessOutputType.STDOUT);
    final String shortenedLine = myOutput.get(ProcessOutputTypes.STDOUT).toList().get(0);
    Assert.assertEquals(shortenedLine.length(), maxSize);
    Assert.assertTrue(shortenedLine.startsWith(string));
  }

  public void testTcMessageRedundantNewLine() {
    mySplitter = createEventSplitter(false, true);
    final ProcessOutputType stdout = ProcessOutputType.STDOUT;
    mySplitter.process("hello\n", stdout);
    mySplitter.process("world\n", stdout);
    final String message = ServiceMessage.asString("testStart", Collections.emptyMap());
    mySplitter.process("\n" + message + "\n", stdout);
    mySplitter.process("hi", stdout);
    mySplitter.flush();
    final String[] strings = myOutput.get(stdout).toArray();
    Assert.assertArrayEquals(new String[]{"hello\n", "world\n", message+ "\n", "hi"}, strings);
  }

  public void testLongMessage() throws ParseException {
    final int maxSize = ConsoleBuffer.getCycleBufferSize();
    final String string = "abc|n";
    final String longString = StringUtil.repeat(string, maxSize);
    final String junk = "QWE";
    final String message =
      String.format("##teamcity[testFailed name='someTest' expected='%s' actual='%s']\n", longString, longString.replaceFirst("abc", junk));

    mySplitter.process(message, ProcessOutputType.STDOUT);
    final String shortenedLine = myOutput.get(ProcessOutputTypes.STDOUT).toList().get(0);
    final ServiceMessage shortenedMessage = ServiceMessageUtil.parse(shortenedLine, true);
    Assert.assertTrue("Failed to shorten message", shortenedMessage.toString().length() <= maxSize);
    final Map<String, String> attrs = shortenedMessage.getAttributes();
    Assert.assertEquals(attrs.get("expected").replaceFirst("abc", junk), attrs.get("actual"));
  }


  public void testMessageEndFlush() {
    final String text = "hello##";
    mySplitter.process(text, ProcessOutputTypes.STDOUT);
    Assert.assertArrayEquals("Text prior to service message start prefix are flushed",
                             new String[]{"hello"}, myOutput.get(ProcessOutputTypes.STDOUT).toArray());
    mySplitter.flush();
    Assert.assertArrayEquals("Rest of the string not flushed after explicit #flush call",
                             new String[]{"hello", "##"}, myOutput.get(ProcessOutputTypes.STDOUT).toArray());
  }

  public void testCharStream() {
    final String messages = "##teamcity[start bar='1']\nmessage\nanothermessage##teamcity[end]\nfuu\n";
    for (int step = 1; step < 20 + 1; step++) {
      int i = 0;
      final int stringLength = messages.length();
      while (i < stringLength) {
        final String substring = messages.substring(i, Math.min(i + step, stringLength));
        mySplitter.process(substring, ProcessOutputTypes.STDOUT);
        i += step;
      }
      mySplitter.flush();
      final List<String> actual = myOutput.get(ProcessOutputTypes.STDOUT).toList();
      Assert.assertThat(actual, IsCollectionContaining.hasItem("##teamcity[end]\n"));
      Assert.assertThat(actual, IsCollectionContaining.hasItem("##teamcity[start bar='1']\n"));
      myOutput.clear();
    }
  }

  public void testFlushOnNewLineOnlyModeTcMessage() {
    mySplitter = createEventSplitter(true, false);
    mySplitter.process("a", ProcessOutputTypes.STDOUT);
    mySplitter.process("bc", ProcessOutputTypes.STDOUT);
    mySplitter.process("d##teamcity[start]\n", ProcessOutputTypes.STDOUT);
    mySplitter.process("bc", ProcessOutputTypes.STDOUT);
    mySplitter.flush();
    Assert.assertEquals(
      "Must be flushed on new line and TC message start",
      Arrays.asList("abcd", "##teamcity[start]\n", "bc"),
      myOutput.get(ProcessOutputTypes.STDOUT).toList());
  }

  public void testFlushOnNewLineOnlyMode() {
    mySplitter = createEventSplitter(true, false);
    for (ProcessOutputType key : new ProcessOutputType[]{ProcessOutputType.STDOUT, ProcessOutputType.STDERR}) {
      mySplitter.process("a\nbc\n", key);
      mySplitter.process("a", key);
      mySplitter.process("bc", key);
      mySplitter.process("d\na", key);
      mySplitter.process("bc", key);
      mySplitter.flush();
      Assert.assertEquals(
        "Must be flushed on new line only in " + key,
        Arrays.asList("a\n", "bc\n", "abcd\n", "abc"),
        myOutput.get(key).toList());
      myOutput.clear();
    }

    mySplitter.process("stdout_message", ProcessOutputTypes.STDOUT);
    mySplitter.process("very ", ProcessOutputTypes.STDERR);
    mySplitter.process("long ", ProcessOutputTypes.STDERR);
    mySplitter.process("\n", ProcessOutputTypes.STDOUT);
    mySplitter.process("line\n", ProcessOutputTypes.STDERR);

    Assert.assertEquals(
      "Stderr and stdout must be processed separately",
      Collections.singletonList("very long line\n"),
      myOutput.get(ProcessOutputTypes.STDERR).toList());

    Assert.assertEquals(
      "Stderr and stdout must be processed separately",
      Collections.singletonList("stdout_message\n"),
      myOutput.get(ProcessOutputTypes.STDOUT).toList());
  }

  /**
   * When tc message is in the middle of line it should reported as separate line like if it has \n before it
   */
  public void testMessageInTheMiddleOfLine() {
    mySplitter.process("\nStarting...\n##teamcity[name1]\nDone 1\n\n##teamcity[name2]\nDone 2", ProcessOutputTypes.STDOUT);
    mySplitter.process("##teamcity[name3]\nTest print##teamcity[message key='spam']\n", ProcessOutputTypes.STDOUT);
    mySplitter.process("##teamcity[name5]\n", ProcessOutputTypes.STDOUT);
    mySplitter.process("##teamcity[name6]\nInfo##teamcity[name7]\n", ProcessOutputTypes.STDOUT);
    final String[] stdout = myOutput.get(ProcessOutputTypes.STDOUT).toArray();
    Assert.assertArrayEquals(new String[]{
      "\n", "Starting...\n", "##teamcity[name1]\n", "Done 1\n", "\n", "##teamcity[name2]\n", "Done 2",
      "##teamcity[name3]\n", "Test print", "##teamcity[message key='spam']\n",
      "##teamcity[name5]\n",
      "##teamcity[name6]\n", "Info", "##teamcity[name7]\n"
    }, stdout);
    for (String prefix : new String[]{"...", "", "... ", "##", " ##", "##team##teamcity[foo]\n"}) {
      final String testStarted = ServiceMessageBuilder.testStarted("myTest").toString();
      final String testEnded = ServiceMessageBuilder.testFinished("myTest").toString();

      mySplitter.process(prefix, ProcessOutputTypes.STDOUT);
      mySplitter.process(prefix + testStarted, ProcessOutputTypes.STDOUT);
      mySplitter.process(testEnded, ProcessOutputTypes.STDOUT);

      mySplitter.flush();

      final List<String> output = myOutput.get(ProcessOutputTypes.STDOUT).toList();
      final String messagePrefix = ServiceMessage.SERVICE_MESSAGE_START;
      Assert.assertThat(output, everyItem(either(startsWith(messagePrefix)).or(not(containsString(messagePrefix)))));
      Assert.assertThat(output, hasItems(testStarted, testEnded));
      myOutput.clear();
    }
  }

  public void testSeveralServiceMessagesInOneLine() {
    mySplitter.process("##teamcity[name1]##teamcity[name2]##teamcity[name3]##teamcit", ProcessOutputTypes.STDOUT);
    Assert.assertEquals(List.of("##teamcity[name1]", "##teamcity[name2]"),
                        myOutput.get(ProcessOutputTypes.STDOUT).toList());
    mySplitter.process("y[name4]\n", ProcessOutputTypes.STDOUT);
    Assert.assertEquals(List.of("##teamcity[name1]", "##teamcity[name2]", "##teamcity[name3]", "##teamcity[name4]\n"),
                        myOutput.get(ProcessOutputTypes.STDOUT).toList());
  }

  public void testEmittingServiceMessagesPromptly() {
    mySplitter.process("Foo ##teamcity[name1]\n##team", ProcessOutputTypes.STDOUT);
    Assert.assertEquals(List.of("Foo ", "##teamcity[name1]\n"),
                        myOutput.get(ProcessOutputTypes.STDOUT).toList());
    mySplitter.process("city[name2]\n", ProcessOutputTypes.STDOUT);
    Assert.assertEquals(List.of("Foo ", "##teamcity[name1]\n", "##teamcity[name2]\n"),
                        myOutput.get(ProcessOutputTypes.STDOUT).toList());
  }

  public void testStderrNotBufferingServiceMessage() {
    mySplitter = createEventSplitter(false, false);
    mySplitter.process("Some stderr", ProcessOutputTypes.STDERR);
    Assert.assertEquals(List.of("Some stderr"),
                        myOutput.get(ProcessOutputTypes.STDERR).toList());
    mySplitter.process("output\nFoo ##team", ProcessOutputTypes.STDERR);
    Assert.assertEquals(List.of("Some stderr", "output\n", "Foo ##team"),
                        myOutput.get(ProcessOutputTypes.STDERR).toList());
  }

  public void testReadingSeveralStreams() {
    Map<ProcessOutputType, List<String>> written = new ConcurrentHashMap<>();
    for (ProcessOutputType each : ALL_TYPES) {
      written.put(each, new ArrayList<>());
      Random r = new Random(123);
      for (int i = 0; i < 1000; i++) {
        String s = StringUtil.repeat("A", 100 + r.nextInt(1000));
        if (r.nextInt(2) == 1) s += "\n";

        mySplitter.process(s, each);
        written.get(each).add(s);
      }
    }

    mySplitter.flush();

    for (ProcessOutputType eachType : ALL_TYPES) {
      assertOrderedEquals(myOutput.get(eachType).toList(), written.get(eachType));
    }
  }

  public void testReadingColoredStreams() throws Exception {
    final Map<Key, List<String>> written = new ConcurrentHashMap<>();
    for (final Key type : ALL_TYPES) {
      written.put(type, new ArrayList<>());
      execute(() -> {
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
          String s = StringUtil.repeat("A", 100 + r.nextInt(1000)) + "\n";

          final Key outputType;
          if (type == ProcessOutputTypes.STDOUT) {
            outputType = ALL_STDOUT_KEYS.get(r.nextInt(2));
          }
          else if (type == ProcessOutputTypes.STDERR) {
            outputType = ALL_STDERR_KEYS.get(r.nextInt(2));
          }
          else {
            outputType = type;
          }
          String prefix = type.toString() + ":";
          mySplitter.process(prefix, type);
          mySplitter.process(s, outputType);
          written.get(type).add(prefix);
          written.get(type).add(s);
        }
      }).get();
    }

    mySplitter.flush();

    for (Key eachType : ALL_TYPES) {
      assertOrderedEquals(myOutput.get(eachType).toList(), written.get(eachType));
    }
  }

  public void testFlushing() throws Exception {
    final Semaphore written = new Semaphore(0);
    final Semaphore read = new Semaphore(0);
    final Map<Key<?>, Integer> numOfProcessCalls = new HashMap<>();

    final AtomicBoolean isFinished = new AtomicBoolean();
    List<Future<?>> futures = new ArrayList<>();

    for (final Key<?> each : ALL_TYPES) {
      futures.add(execute(() -> {
        int i = 0;
        while (!isFinished.get()) {
          mySplitter.process(StringUtil.repeat("A", 100), each);
          i++;
          numOfProcessCalls.put(each, i);
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
            List<String> out = myOutput.get(each).toList();
            if (!out.isEmpty()) {
              Integer size = numOfProcessCalls.get(each);
              assert size != null : "No side for " + each;
              assertSize(size, out);
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
    PlatformTestUtil.startPerformanceTest("Flushing lot's of fragments", 10, mySplitter::flush)
      .setup(() -> {
        for (int i = 0; i < 10_000; i++) {
          mySplitter.process("some string without slash n appending in raw, attempt: " + i + "; ", ProcessOutputTypes.STDOUT);
        }
      })
      .useLegacyScaling()
      .assertTiming();
  }

  public void testPerformanceSimple() {
    String testStarted = ServiceMessageBuilder.testStarted("myTest").toString() + "\n";
    mySplitter = new OutputEventSplitter() {
      @Override
      public void onTextAvailable(@NotNull String text, @NotNull Key<?> outputType) {

      }
    };
    PlatformTestUtil.startPerformanceTest("print newlines with backspace", 5000, () -> {
      for (int i = 0; i < 2_000_000; i++) {
        mySplitter.process("some string without slash n appending in raw, attempt: " + i + "; ", ProcessOutputTypes.STDOUT);
        mySplitter.process(testStarted, ProcessOutputTypes.STDOUT);
      }
    }).assertTiming();
  }

  private static Future<?> execute(final Runnable runnable) {
    return ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }
}
