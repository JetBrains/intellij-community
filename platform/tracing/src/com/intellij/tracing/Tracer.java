// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tracing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Tracer {
  private static long tracingStartNs;
  private static long tracingStartMs;
  private static final AtomicLong eventId = new AtomicLong();
  private static final ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();
  private static volatile int pid;
  private static volatile long durationThreshold;
  private static volatile FileState fileState = null;
  private static volatile boolean running = false;
  private static ScheduledExecutorService executor = null;
  private static Thread shutdownHook;

  private Tracer() { }


  public static DelayedSpan start(Supplier<String> nameSupplier) {
    long eventId = Tracer.eventId.getAndIncrement();
    long threadId = Thread.currentThread().getId();
    long startNs = System.nanoTime();
    return new DelayedSpan(eventId, threadId, nameSupplier, startNs);
  }

  public static Span start(String name) {
    long eventId = Tracer.eventId.getAndIncrement();
    long threadId = Thread.currentThread().getId();
    long startNs = System.nanoTime();
    return new Span(eventId, threadId, name, startNs);
  }

  public static void runTracer(int pid, Path filePath, long threshold, Consumer<Exception> exceptionHandler) throws IOException {
    if (running) throw new IllegalStateException("Tracer already started");
    tracingStartMs = System.currentTimeMillis();
    tracingStartNs = System.nanoTime();
    Files.createDirectories(filePath.getParent());
    FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile());
    OutputStreamWriter writer = new OutputStreamWriter(new BufferedOutputStream(fileOutputStream), StandardCharsets.UTF_8);
    fileState = new FileState(writer);
    durationThreshold = threshold;
    Tracer.pid = pid;
    executor = createExecutor();
    FlushingTask flushingTask = new FlushingTask(fileState, false, exceptionHandler);
    executor.scheduleAtFixedRate(flushingTask, 5, 5, TimeUnit.SECONDS);
    shutdownHook = new Thread(new FlushingTask(fileState, true, exceptionHandler), "Shutdown hook trace flusher");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    running = true;
  }

  public static void finishTracer(Consumer<Exception> exceptionHandler) {
    if (fileState == null) return;
    new FlushingTask(fileState, true, exceptionHandler).run();
    fileState = null;
    executor.shutdown();
    executor = null;
    Runtime.getRuntime().removeShutdownHook(shutdownHook);
    running = false;
  }

  public static boolean isRunning() {
    return running;
  }

  private static ScheduledExecutorService createExecutor() {
    return Executors.newScheduledThreadPool(1, r -> {
      Thread thread = new Thread(r, "Trace flusher");
      thread.setDaemon(true);
      return thread;
    });
  }

  public static class DelayedSpan {
    final long eventId;
    final long threadId;
    final Supplier<String> nameSupplier;
    final long startTimeNs;

    public DelayedSpan(long eventId, long threadId, Supplier<String> nameSupplier, long startTimeNs) {
      this.eventId = eventId;
      this.threadId = threadId;
      this.nameSupplier = nameSupplier;
      this.startTimeNs = startTimeNs;
    }

    public void complete() {
      if (running) {
        Span span = new Span(eventId, threadId, nameSupplier.get(), startTimeNs);
        span.complete();
      }
    }
  }

  public static class Span {
    final long eventId;
    final long threadId;
    final String name;
    final long startTimeNs;
    long finishTimeNs;

    public Span(long eventId, long threadId, String name, long startTimeNs) {
      this.eventId = eventId;
      this.threadId = threadId;
      this.name = name;
      this.startTimeNs = startTimeNs;
    }

    public void complete() {
      if (running) {
        finishTimeNs = System.nanoTime();
        if (getDuration() > durationThreshold) {
          spans.offerLast(this);
        }
      }
    }

    /**
     * If event has been started on one thread and finished on the other it is not guaranteed to have non-negative duration
     */
    long getDuration() {
      return finishTimeNs - startTimeNs;
    }
  }

  private static class FileState {
    final Writer writer;
    boolean openBracketWritten = false;
    boolean finished = false;

    private FileState(Writer writer) {
      this.writer = writer;
    }
  }

  private static class FlushingTask implements Runnable {
    private final FileState fileState;
    private final boolean shouldFinish;
    private final Consumer<Exception> myExceptionHandler;

    private FlushingTask(FileState fileState, boolean shouldFinish, Consumer<Exception> exceptionHandler) {
      this.fileState = fileState;
      this.shouldFinish = shouldFinish;
      myExceptionHandler = exceptionHandler;
    }

    @Override
    public void run() {
      if (fileState == null) return;
      synchronized (fileState) {
        if (fileState.finished) return;
        try {
          if (!fileState.openBracketWritten) {
            fileState.writer.write("[\n");
            fileState.openBracketWritten = true;
          }
          while (true) {
            Span span = spans.pollLast();
            if (span == null) break;
            fileState.writer.write(serialize(span, true));
            fileState.writer.write(serialize(span, false));
          }
          if (shouldFinish) {
            fileState.writer.write("]");
          }
          fileState.writer.flush();
        }
        catch (IOException e) {
          myExceptionHandler.accept(e);
        }
      }
    }

    private static String serialize(Span span, boolean isStart) {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"name\": \"")
        .append(span.name)
        .append("\", \"cat\": \"PERF\", \"ph\": ");
      if (isStart) {
        sb.append("\"B\"");
      }
      else {
        sb.append("\"E\"");
      }
      sb.append(", \"pid\": ").append(pid)
        .append(", \"tid\": ").append(span.threadId)
        .append(", \"ts\": ");
      if (isStart) {
        sb.append(getTimeUs(span.startTimeNs));
      }
      else {
        sb.append(getTimeUs(span.finishTimeNs));
      }
      return sb.append("},\n").toString();
    }
  }

  static long getTimeUs(long timeNs) {
    return (tracingStartMs * 1_000_000 - tracingStartNs + timeNs) / 1000;
  }
}
