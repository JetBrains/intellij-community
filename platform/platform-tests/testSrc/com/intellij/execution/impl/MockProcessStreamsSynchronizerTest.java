// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.Disposable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MockProcessStreamsSynchronizerTest extends LightPlatformTestCase {

  private static final long AWAIT_SAME_STREAM_TEXT_MILLIS = TimeUnit.NANOSECONDS.toMillis(ProcessStreamsSynchronizer.AWAIT_SAME_STREAM_TEXT_NANO);

  private MockProcessStreamsSynchronizer mySynchronizer;

  public void testBasic() {
    mySynchronizer = new MockProcessStreamsSynchronizer(getTestRootDisposable());
    mySynchronizer.doWhenStreamsSynchronized("Hello stdout 1\n", ProcessOutputType.STDOUT, 0);
    mySynchronizer.doWhenStreamsSynchronized("Hello stdout 2\n", ProcessOutputType.STDOUT, 1);
    mySynchronizer.doWhenStreamsSynchronized("Hello stderr 1\n", ProcessOutputType.STDERR, 2);
    assertFlushedChunks(new FlushedChunk("Hello stdout 1\n", ProcessOutputType.STDOUT, 0),
                        new FlushedChunk("Hello stdout 2\n", ProcessOutputType.STDOUT, 1));
    mySynchronizer.advanceTimeTo(1 + AWAIT_SAME_STREAM_TEXT_MILLIS);
    assertFlushedChunks(new FlushedChunk("Hello stderr 1\n", ProcessOutputType.STDERR, 1 + AWAIT_SAME_STREAM_TEXT_MILLIS));
    assertNoPendingChunks();
  }

  public void testSimpleRun() {
    mySynchronizer = new MockProcessStreamsSynchronizer(getTestRootDisposable());
    mySynchronizer.doWhenStreamsSynchronized("java HelloWorld\n", ProcessOutputType.SYSTEM, 10);

    mySynchronizer.doWhenStreamsSynchronized("info:", ProcessOutputType.STDOUT, 10);
    mySynchronizer.doWhenStreamsSynchronized("warn:", ProcessOutputType.STDERR, 14);
    mySynchronizer.doWhenStreamsSynchronized("Error\n", ProcessOutputType.STDERR, 14);
    mySynchronizer.doWhenStreamsSynchronized("Hello from stdout", ProcessOutputType.STDOUT, 15);
    mySynchronizer.doWhenStreamsSynchronized("\n", ProcessOutputType.STDOUT, 16);

    assertFlushedChunks(new FlushedChunk("java HelloWorld\n", ProcessOutputType.SYSTEM, 10),
                        new FlushedChunk("info:", ProcessOutputType.STDOUT, 10),
                        new FlushedChunk("Hello from stdout", ProcessOutputType.STDOUT, 15),
                        new FlushedChunk("\n", ProcessOutputType.STDOUT, 16));

    mySynchronizer.advanceTimeTo(10 + AWAIT_SAME_STREAM_TEXT_MILLIS);
    assertFlushedChunks(new FlushedChunk("warn:", ProcessOutputType.STDERR, 10 + AWAIT_SAME_STREAM_TEXT_MILLIS),
                        new FlushedChunk("Error\n", ProcessOutputType.STDERR, 10 + AWAIT_SAME_STREAM_TEXT_MILLIS));

    mySynchronizer.doWhenStreamsSynchronized("info:", ProcessOutputType.STDOUT, 21);
    mySynchronizer.doWhenStreamsSynchronized("2\n", ProcessOutputType.STDOUT, 21);

    mySynchronizer.doWhenStreamsSynchronized("Another error", ProcessOutputType.STDERR, 22);
    assertFlushedChunks(new FlushedChunk("Another error", ProcessOutputType.STDERR, 22));
    mySynchronizer.doWhenStreamsSynchronized("\n", ProcessOutputType.STDERR, 22);
    assertFlushedChunks(new FlushedChunk("\n", ProcessOutputType.STDERR, 22));
    mySynchronizer.doWhenStreamsSynchronized("Process exited", ProcessOutputType.SYSTEM, 22);

    mySynchronizer.advanceTimeTo(14 + AWAIT_SAME_STREAM_TEXT_MILLIS);
    assertFlushedChunks(new FlushedChunk("info:", ProcessOutputType.STDOUT, 14 + AWAIT_SAME_STREAM_TEXT_MILLIS),
                        new FlushedChunk("2\n", ProcessOutputType.STDOUT, 14 + AWAIT_SAME_STREAM_TEXT_MILLIS),
                        new FlushedChunk("Process exited", ProcessOutputType.SYSTEM, 14 + AWAIT_SAME_STREAM_TEXT_MILLIS));
    assertNoPendingChunks();
  }

  public void testNewlineArrivedLater() {
    mySynchronizer = new MockProcessStreamsSynchronizer(getTestRootDisposable());
    long startTime = 10;
    Assert.assertTrue(AWAIT_SAME_STREAM_TEXT_MILLIS >= 3);
    mySynchronizer.doWhenStreamsSynchronized("info:", ProcessOutputType.STDOUT, startTime);
    mySynchronizer.doWhenStreamsSynchronized("error\n", ProcessOutputType.STDERR, startTime + 1);
    mySynchronizer.doWhenStreamsSynchronized("rest of info\n", ProcessOutputType.STDOUT, startTime + 2);

    assertFlushedChunks(new FlushedChunk("info:", ProcessOutputType.STDOUT, startTime),
                        new FlushedChunk("rest of info\n", ProcessOutputType.STDOUT, startTime + 2));
    mySynchronizer.advanceTimeTo(startTime + AWAIT_SAME_STREAM_TEXT_MILLIS);
    assertFlushedChunks(new FlushedChunk("error\n", ProcessOutputType.STDERR, startTime + AWAIT_SAME_STREAM_TEXT_MILLIS));
    assertNoPendingChunks();
  }

  public void testNewlineArrivedAfterFirstScheduledProcessing() {
    mySynchronizer = new MockProcessStreamsSynchronizer(getTestRootDisposable());
    long startTime = 10;
    Assert.assertTrue(AWAIT_SAME_STREAM_TEXT_MILLIS >= 3);
    mySynchronizer.doWhenStreamsSynchronized("info:", ProcessOutputType.STDOUT, startTime);
    mySynchronizer.doWhenStreamsSynchronized("error\n", ProcessOutputType.STDERR, startTime + AWAIT_SAME_STREAM_TEXT_MILLIS);
    assertFlushedChunks(new FlushedChunk("info:", ProcessOutputType.STDOUT, startTime));

    mySynchronizer.doWhenStreamsSynchronized("rest of info\n", ProcessOutputType.STDOUT, startTime + 1 + AWAIT_SAME_STREAM_TEXT_MILLIS);
    assertFlushedChunks(new FlushedChunk("rest of info\n", ProcessOutputType.STDOUT, startTime + 1 + AWAIT_SAME_STREAM_TEXT_MILLIS),
                        new FlushedChunk("error\n", ProcessOutputType.STDERR, startTime + 1 + AWAIT_SAME_STREAM_TEXT_MILLIS));
    assertNoPendingChunks();
  }

  public void testPerformanceSingleStream() {
    Benchmark.newBenchmark("single stream", () -> {
      mySynchronizer = new MockProcessStreamsSynchronizer(getTestRootDisposable());
      long nowTimeMillis = 10;
      for (int i = 0; i < 10_000_000; i++) {
        mySynchronizer.doWhenStreamsSynchronized("Std", ProcessOutputType.STDOUT, nowTimeMillis);
        mySynchronizer.doWhenStreamsSynchronized("Out\n", ProcessOutputType.STDOUT, nowTimeMillis + 5);
        mySynchronizer.doWhenStreamsSynchronized("#2 Std", ProcessOutputType.STDOUT, nowTimeMillis + 6);
        mySynchronizer.doWhenStreamsSynchronized("#2 Out\n", ProcessOutputType.STDOUT, nowTimeMillis + 7);
        mySynchronizer.doWhenStreamsSynchronized("stdout again", ProcessOutputType.STDOUT, nowTimeMillis + 8);
        mySynchronizer.advanceTimeTo(nowTimeMillis + 8);
        assertFlushedChunks(
          new FlushedChunk("Std", ProcessOutputType.STDOUT, nowTimeMillis),
          new FlushedChunk("Out\n", ProcessOutputType.STDOUT, nowTimeMillis + 5),
          new FlushedChunk("#2 Std", ProcessOutputType.STDOUT, nowTimeMillis + 6),
          new FlushedChunk("#2 Out\n", ProcessOutputType.STDOUT, nowTimeMillis + 7),
          new FlushedChunk("stdout again", ProcessOutputType.STDOUT, nowTimeMillis + 8)
        );
        assertNoPendingChunks();
        nowTimeMillis += 8;
      }
    }).start();
  }

  public void testPerformanceTwoStreams() {
    Benchmark.newBenchmark("two streams", () -> {
      mySynchronizer = new MockProcessStreamsSynchronizer(getTestRootDisposable());
      long nowTimeMillis = 10;
      for (int i = 0; i < 10_000_000; i++) {
        mySynchronizer.doWhenStreamsSynchronized("Std", ProcessOutputType.STDOUT, nowTimeMillis);
        mySynchronizer.doWhenStreamsSynchronized("Std", ProcessOutputType.STDERR, nowTimeMillis + 4);
        mySynchronizer.doWhenStreamsSynchronized("Out\n", ProcessOutputType.STDOUT, nowTimeMillis + 5);
        mySynchronizer.doWhenStreamsSynchronized("Err\n", ProcessOutputType.STDERR, nowTimeMillis + 7);
        mySynchronizer.doWhenStreamsSynchronized("stdout again", ProcessOutputType.STDOUT, nowTimeMillis + 6 + AWAIT_SAME_STREAM_TEXT_MILLIS);
        mySynchronizer.advanceTimeTo(nowTimeMillis + 8 + 2 * AWAIT_SAME_STREAM_TEXT_MILLIS);
        assertFlushedChunks(
          new FlushedChunk("Std", ProcessOutputType.STDOUT, nowTimeMillis),
          new FlushedChunk("Out\n", ProcessOutputType.STDOUT, nowTimeMillis + 5),
          new FlushedChunk("Std", ProcessOutputType.STDERR, nowTimeMillis + AWAIT_SAME_STREAM_TEXT_MILLIS),
          new FlushedChunk("Err\n", ProcessOutputType.STDERR, nowTimeMillis + AWAIT_SAME_STREAM_TEXT_MILLIS),
          new FlushedChunk("stdout again", ProcessOutputType.STDOUT, nowTimeMillis + 7 + AWAIT_SAME_STREAM_TEXT_MILLIS)
        );
        assertNoPendingChunks();
        nowTimeMillis += 8 + 2 * AWAIT_SAME_STREAM_TEXT_MILLIS;
      }
    }).start();
  }

  private void assertFlushedChunks(FlushedChunk @NotNull ... expectedFlushedChunks) {
    Assert.assertEquals(List.of(expectedFlushedChunks), mySynchronizer.getFlushedChunksAndClear());
  }

  private void assertNoPendingChunks() {
    Assert.assertEquals(0, mySynchronizer.myFlushedChunkCount - mySynchronizer.myRequestedChunkCount);
  }

  private static class MockProcessStreamsSynchronizer extends ProcessStreamsSynchronizer {

    private long myNowTimeNano;
    private long myNextScheduledProcessingTimeNano = -1;
    private final List<FlushedChunk> myFlushedChunks = new ArrayList<>();
    private int myRequestedChunkCount = 0;
    private int myFlushedChunkCount = 0;

    MockProcessStreamsSynchronizer(@NotNull Disposable parentDisposable) {
      super(parentDisposable);
    }

    void doWhenStreamsSynchronized(@NotNull String text, @NotNull ProcessOutputType outputType, long nowTimeMillis) {
      myRequestedChunkCount++;
      advanceTimeTo(nowTimeMillis);
      super.doWhenStreamsSynchronized(text, outputType, () -> {
        myFlushedChunkCount++;
        myFlushedChunks.add(new FlushedChunk(text, outputType, TimeUnit.NANOSECONDS.toMillis(getNanoTime())));
      });
    }

    @Override
    protected long getNanoTime() {
      return myNowTimeNano;
    }

    @Override
    protected boolean isProcessingScheduled() {
      return myNextScheduledProcessingTimeNano != -1;
    }

    @Override
    protected void scheduleProcessPendingChunks(long delayNano) {
      Assert.assertEquals(-1, myNextScheduledProcessingTimeNano);
      myNextScheduledProcessingTimeNano = myNowTimeNano + delayNano;
    }

    @Override
    public void waitForAllFlushed() {
      LOG.error("Use #advanceTimeTo for " + MockProcessStreamsSynchronizer.class);
    }

    private void advanceTimeTo(long timeMillis) {
      long nowTimeNano = TimeUnit.MILLISECONDS.toNanos(timeMillis);
      assert nowTimeNano >= myNowTimeNano;
      tryAdvanceTimeToNextScheduledProcessing(nowTimeNano);
      myNowTimeNano = nowTimeNano;
    }

    private void tryAdvanceTimeToNextScheduledProcessing(long nextDataTimeNano) {
      if (myNextScheduledProcessingTimeNano != -1 && nextDataTimeNano >= myNextScheduledProcessingTimeNano) {
        myNowTimeNano = myNextScheduledProcessingTimeNano;
        myNextScheduledProcessingTimeNano = -1;
        super.processPendingChunks(myNowTimeNano);
      }
    }

    @NotNull
    List<FlushedChunk> getFlushedChunksAndClear() {
      List<FlushedChunk> chunks = new ArrayList<>(myFlushedChunks);
      myFlushedChunks.clear();
      return chunks;
    }
  }

  private static final class FlushedChunk {
    private final String myText;
    private final ProcessOutputType myOutputType;
    private final long myFlushTimeMillis;

    private FlushedChunk(@NotNull String text, @NotNull ProcessOutputType outputType, long flushTimeMillis) {
      myText = text;
      myOutputType = outputType;
      myFlushTimeMillis = flushTimeMillis;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FlushedChunk chunk = (FlushedChunk)o;
      return myFlushTimeMillis == chunk.myFlushTimeMillis &&
             myText.equals(chunk.myText) &&
             myOutputType.equals(chunk.myOutputType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myText, myOutputType, myFlushTimeMillis);
    }

    @Override
    public String toString() {
      return "(text='" + myText + '\'' + ", outputType=" + myOutputType + ", flushTimeMillis=" + myFlushTimeMillis + ")";
    }
  }
}
