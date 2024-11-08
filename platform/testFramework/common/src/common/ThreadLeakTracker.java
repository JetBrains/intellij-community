// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.diagnostic.JVMResponsivenessMonitor;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.FlushingDaemon;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.FilePageCacheLockFree;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import java.util.concurrent.locks.LockSupport;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.io.NettyUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

@TestOnly
@Internal
public final class ThreadLeakTracker {
  private ThreadLeakTracker() { }

  private static final MethodHandle getThreads = getThreadsMethodHandle();

  public static @NotNull Map<String, Thread> getThreads() {
    Thread[] threads;
    try {
      // faster than Thread.getAllStackTraces().keySet()
      threads = (Thread[])getThreads.invokeExact();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }

    if (threads.length == 0) {
      return Collections.emptyMap();
    }

    Map<String, Thread> map = new HashMap<>(threads.length);
    for (Thread thread : threads) {
      map.put(thread.getName(), thread);
    }
    return map;
  }

  // contains prefixes of the thread names which are known to be long-running (and thus exempted from the leaking threads' detection)
  private static final Set<String> wellKnownOffenders;

  static {
    @SuppressWarnings({"deprecation", "SpellCheckingInspection"}) List<String> offenders = List.of(
      "ApplicationImpl pooled thread ", // com.intellij.util.concurrency.AppScheduledExecutorService.POOLED_THREAD_PREFIX
      "AWT-EventQueue-",
      "AWT-Shutdown",
      "AWT-Windows",
      "BatchSpanProcessor_WorkerThread", // io.opentelemetry.sdk.trace.export.BatchSpanProcessor.WORKER_THREAD_NAME
      "Batik CleanerThread",
      "BC Entropy Daemon",
      "CefHandlers-",
      "Cidr Symbol Building Thread", // ForkJoinPool com.jetbrains.cidr.lang.symbols.symtable.building.OCBuildingActivityExecutionService
      "Cleaner-0", // Thread[Cleaner-0,8,InnocuousThreadGroup], java.lang.ref.Cleaner in android layoutlib, Java9+
      "CompilerThread0",
      "Coroutines Debugger Cleaner", // kotlinx.coroutines.debug.internal.DebugProbesImpl.startWeakRefCleanerThread
      "dockerjava-netty",
      "External compiler",
      FilePageCacheLockFree.DEFAULT_HOUSEKEEPER_THREAD_NAME,
      "Finalizer",
      FlushingDaemon.NAME,
      "grpc-default-worker-",  // grpc_netty_shaded
      "HttpClient-",  // JRE's HttpClient thread pool is not supposed to be disposed - to reuse connections
      ProcessIOExecutorService.POOLED_THREAD_PREFIX,
      "IDEA Test Case Thread",
      "Image Fetcher ",
      "InnocuousThreadGroup",
      "Java2D Disposer",
      "JNA Cleaner",
      "JobScheduler FJ pool ",
      "JPS thread pool",
      JVMResponsivenessMonitor.MONITOR_THREAD_NAME,
      "Keep-Alive-SocketCleaner", // Thread[Keep-Alive-SocketCleaner,8,InnocuousThreadGroup], JBR-11
      "Keep-Alive-Timer",
      "main",
      "Monitor Ctrl-Break",
      "Netty ",
      "ObjectCleanerThread",
      // see okhttp3.ConnectionPool: "this pool holds up to 5 idle connections which will be evicted after 5 minutes of inactivity"
      "OkHttp ",
      "Okio Watchdog", // Dockers "okio.AsyncTimeout.Watchdog"
      "Periodic tasks thread", // com.intellij.util.concurrency.AppDelayQueue.TransferThread
      "process reaper", // Thread[#46,process reaper(pid7496),10,InnocuousThreadGroup] (since JDK-8279488 part of InnocuousThreadGroup)
      "qtp", // used in tests for mocking via WireMock in integration testing
      "rd throttler", // daemon thread created by com.jetbrains.rd.util.AdditionalApiKt.getTimer
      "Reference Handler",
      "RMI GC Daemon",
      "RMI TCP ",
      "Save classpath indexes for file loader",
      "Shared Index Hash Index Flushing Queue",
      "Signal Dispatcher",
      "tc-okhttp-stream", // Dockers "com.github.dockerjava.okhttp.UnixDomainSocket.recv"
      "testcontainers",
      "timer-int", //serverIm,
      "timer-sys", //clientIm,
      "TimerQueue",
      "UserActivityMonitor thread",
      "VM Periodic Task Thread",
      "VM Thread",
      "YJPAgent-Telemetry"
    );
    validateWhitelistedThreads(offenders);
    wellKnownOffenders = new HashSet<>(offenders);

    try {
      // init zillions of timers in e.g., MacOSXPreferencesFile
      Preferences.userRoot().flush();
    }
    catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  // marks Thread with this name as long-running, which should be ignored from the thread-leaking checks
  public static void longRunningThreadCreated(@NotNull Disposable parentDisposable, @NotNull String @NotNull ... threadNamePrefixes) {
    ContainerUtil.addAll(wellKnownOffenders, threadNamePrefixes);
    Disposer.register(parentDisposable, () -> ContainerUtil.removeAll(wellKnownOffenders, threadNamePrefixes));
  }

  public static void awaitQuiescence() {
    NettyUtil.awaitQuiescenceOfGlobalEventExecutor(100, TimeUnit.SECONDS);
    ShutDownTracker.getInstance().waitFor(100, TimeUnit.SECONDS);
  }

  public static void checkLeak(@NotNull Map<String, Thread> threadsBefore) throws AssertionError {
    // compare threads by name because BoundedTaskExecutor reuses application thread pool for different bounded pools,
    // leaks of which we want to find
    Map<String, Thread> all = getThreads();
    Map<String, Thread> after = new HashMap<>(all);
    after.keySet().removeAll(threadsBefore.keySet());
    Map<Thread, StackTraceElement[]> stackTraces = ContainerUtil.map2Map(
      after.values(),
      thread -> new Pair<>(thread, thread.getStackTrace())
    );
    for (Thread thread : after.values()) {
      waitForThread(thread, stackTraces, all, after);
    }
  }

  private static void waitForThread(Thread thread,
                                    Map<Thread, StackTraceElement[]> stackTraces,
                                    Map<String, Thread> all,
                                    Map<String, Thread> after) {
    if (!shouldWaitForThread(thread)) {
      return;
    }
    long start = System.currentTimeMillis();
    StackTraceElement[] traceBeforeWait = thread.getStackTrace();
    if (shouldIgnore(thread, traceBeforeWait)) {
      return;
    }
    int WAIT_SEC = 10;
    long deadlineMs = start + TimeUnit.SECONDS.toMillis(WAIT_SEC);
    StackTraceElement[] stackTrace = traceBeforeWait;
    while (System.currentTimeMillis() < deadlineMs) {
      // give a blocked thread an opportunity to die if it's stuck doing invokeAndWait()
      if (EDT.isCurrentThreadEdt()) {
        UIUtil.dispatchAllInvocationEvents();
      }
      else {
        UIUtil.pump();
      }
      // after some time, the submitted task can finish and the thread can become idle
      stackTrace = thread.getStackTrace();
      if (shouldIgnore(thread, stackTrace)) break;
      // avoid busy-waiting, otherwise other threads might yield priority to this one (see sleepIfNeededToGivePriorityToAnotherThread)
      LockSupport.parkNanos(10_000_000);
    }

    // check once more because the thread name may be set via race
    stackTraces.put(thread, stackTrace);
    if (shouldIgnore(thread, stackTrace)) {
      return;
    }

    all.keySet().removeAll(after.keySet());
    Map<Thread, StackTraceElement[]> otherStackTraces = ContainerUtil.map2Map(all.values(), t -> Pair.create(t, t.getStackTrace()));

    String trace = PerformanceWatcher.printStacktrace("", thread, stackTrace);
    String traceBefore = PerformanceWatcher.printStacktrace("", thread, traceBeforeWait);

    String internalDiagnostic = internalDiagnostic(stackTrace);

    throw new AssertionError(
      "Thread leaked: " + traceBefore + (trace.equals(traceBefore) ? "" : "(its trace after " + WAIT_SEC + " seconds wait:) " + trace) +
      internalDiagnostic +
      "\n\nLeaking threads dump:\n" + dumpThreadsToString(after, stackTraces) +
      "\n----\nAll other threads dump:\n" + dumpThreadsToString(all, otherStackTraces)
    );
  }

  private static boolean shouldWaitForThread(Thread thread) {
    if (thread == Thread.currentThread()) {
      return false;
    }
    ThreadGroup group = thread.getThreadGroup();
    if (group != null && "system".equals(group.getName()) || !thread.isAlive()) {
      return false;
    }
    return true;
  }

  private static boolean shouldIgnore(Thread thread, StackTraceElement[] stackTrace) {
    if (!thread.isAlive()) return true;
    if (stackTrace.length == 0) return true;
    if (isWellKnownOffender(thread.getName())) return true;

    return isIdleApplicationPoolThread(stackTrace)
           || isIdleCommonPoolThread(thread, stackTrace)
           || isFutureTaskAboutToFinish(stackTrace)
           || isIdleDefaultCoroutineExecutorThread(thread, stackTrace)
           || isCoroutineSchedulerPoolThread(thread, stackTrace)
           || isKotlinCIOSelector(stackTrace)
           || isStarterTestFramework(stackTrace)
           || isJMXRemoteCall(stackTrace)
           || isBuildLogCall(stackTrace);
  }

  private static boolean isWellKnownOffender(String threadName) {
    for (String t : wellKnownOffenders) {
      if (threadName.contains(t)) {
        return true;
      }
    }
    return false;
  }

  // true, if somebody started a new thread via "executeInPooledThread()" and then the thread is waiting for the next task
  private static boolean isIdleApplicationPoolThread(StackTraceElement[] stackTrace) {
    return ContainerUtil.exists(stackTrace, element -> element.getMethodName().equals("getTask")
                                                       && element.getClassName().equals("java.util.concurrent.ThreadPoolExecutor"));
  }

  private static boolean isKotlinCIOSelector(StackTraceElement[] stackTrace) {
    return ContainerUtil.exists(stackTrace, element -> element.getMethodName().equals("select")
                                                       && element.getClassName().equals("io.ktor.network.selector.ActorSelectorManager"));
  }

  private static boolean isIdleCommonPoolThread(Thread thread, StackTraceElement[] stackTrace) {
    if (!ForkJoinWorkerThread.class.isAssignableFrom(thread.getClass())) {
      return false;
    }
    boolean insideAwaitWork = ContainerUtil.exists(
      stackTrace,
      element -> element.getMethodName().equals("awaitWork")
                 && element.getClassName().equals("java.util.concurrent.ForkJoinPool")
    );
    if (insideAwaitWork) return true;
    //java.lang.AssertionError: Thread leaked: Thread[ForkJoinPool.commonPool-worker-13,4,main] (alive) WAITING
    //--- its stacktrace:
    // at java.base@11.0.6/jdk.internal.misc.Unsafe.park(Native Method)
    // at java.base@11.0.6/java.util.concurrent.locks.LockSupport.park(LockSupport.java:194)
    // at java.base@11.0.6/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1628)
    // at java.base@11.0.6/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:177)
    return stackTrace.length > 2
           // can be both sun.misc.Unsafe and jdk.internal.misc.Unsafe on depending on the jdk
           && stackTrace[0].getClassName().endsWith(".Unsafe") && stackTrace[0].getMethodName().equals("park")
           && stackTrace[1].getClassName().equals("java.util.concurrent.locks.LockSupport") && stackTrace[1].getMethodName().equals("park")
           && stackTrace[2].getClassName().equals("java.util.concurrent.ForkJoinPool") && stackTrace[2].getMethodName().equals("runWorker");
  }

  // in newer JDKs strange long hangups observed in Unsafe.unpark:
  // "Document Committing Pool" (alive) TIMED_WAITING
  //	at sun.misc.Unsafe.unpark(Native Method)
  //	at java.util.concurrent.locks.LockSupport.unpark(LockSupport.java:141)
  //	at java.util.concurrent.FutureTask.finishCompletion(FutureTask.java:372)
  //	at java.util.concurrent.FutureTask.set(FutureTask.java:233)
  //	at java.util.concurrent.FutureTask.run(FutureTask.java:274)
  //	at com.intellij.util.concurrency.BoundedTaskExecutor.doRun(BoundedTaskExecutor.java:207)
  //	at com.intellij.util.concurrency.BoundedTaskExecutor.access$100(BoundedTaskExecutor.java:29)
  //	at com.intellij.util.concurrency.BoundedTaskExecutor$1.lambda$run$0(BoundedTaskExecutor.java:185)
  //	at com.intellij.util.concurrency.BoundedTaskExecutor$1$$Lambda$157/1473781324.run(Unknown Source)
  //	at com.intellij.util.ConcurrencyUtil.runUnderThreadName(ConcurrencyUtil.java:208)
  //	at com.intellij.util.concurrency.BoundedTaskExecutor$1.run(BoundedTaskExecutor.java:181)
  //	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
  //	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
  //	at java.lang.Thread.run(Thread.java:748)
  private static boolean isFutureTaskAboutToFinish(StackTraceElement[] stackTrace) {
    if (stackTrace.length < 5) {
      return false;
    }

    return stackTrace[0].getClassName().equals("sun.misc.Unsafe")
           && stackTrace[0].getMethodName().equals("unpark")
           && stackTrace[2].getClassName().equals("java.util.concurrent.FutureTask")
           && stackTrace[2].getMethodName().equals("finishCompletion");
  }

  /**
   * at sun.misc.Unsafe.park(Native Method)
   * at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:215)
   * at kotlinx.coroutines.DefaultExecutor.run(DefaultExecutor.kt:83)
   * at java.lang.Thread.run(Thread.java:748)
   */
  private static boolean isIdleDefaultCoroutineExecutorThread(Thread thread, StackTraceElement[] stackTrace) {
    if (stackTrace.length != 4) {
      return false;
    }
    return "kotlinx.coroutines.DefaultExecutor".equals(thread.getName())
           && (stackTrace[0].getClassName().equals("sun.misc.Unsafe") || stackTrace[0].getClassName().equals("jdk.internal.misc.Unsafe"))
           && stackTrace[0].getMethodName().equals("park")
           && stackTrace[2].getClassName().equals("kotlinx.coroutines.DefaultExecutor")
           && stackTrace[2].getMethodName().equals("run");
  }

  private static boolean isCoroutineSchedulerPoolThread(Thread thread, StackTraceElement[] stackTrace) {
    if (!"kotlinx.coroutines.scheduling.CoroutineScheduler$Worker".equals(thread.getClass().getName())) {
      return false;
    }
    //noinspection UnnecessaryLocalVariable
    boolean insideCpuWorkerIdle = ContainerUtil.exists(
      stackTrace,
      element -> element.getMethodName().equals("park")
                 && element.getClassName().equals("kotlinx.coroutines.scheduling.CoroutineScheduler$Worker")
    );
    return insideCpuWorkerIdle;
  }

  /**
   * Starter framework [intellij.ide.starter] / [intellij.ide.starter.extended] registers its own JUnit listeners.
   * A listener's order of execution isn't defined, so when the thread leak detector detects a leak,
   * the listener from starter might not have a chance to clean up after tests.
   */
  private static boolean isStarterTestFramework(StackTraceElement[] stackTrace) {
    // java.lang.AssertionError: Thread leaked: Thread[Redirect stderr,5,main] (alive) RUNNABLE
    //--- its stacktrace:
    // ...
    // at app//com.intellij.ide.starter.process.exec.ProcessExecutor$redirectProcessOutput$1.invoke(ProcessExecutor.kt:40)
    // at app//kotlin.concurrent.ThreadsKt$thread$thread$1.run(Thread.kt:30)

    return ContainerUtil.exists(
      stackTrace,
      element -> element.getClassName().contains("com.intellij.ide.starter")
    );
  }

  /**
   * {@code com.intellij.driver.client.*} is using JMX. That might lead to long-living tasks.
   */
  private static boolean isJMXRemoteCall(StackTraceElement[] stackTrace) {
    // Thread leaked: Thread[JMX client heartbeat 3,5,main] (alive) TIMED_WAITING
    // --- its stacktrace:
    // at java.base@17.0.9/java.lang.Thread.sleep(Native Method)
    // at java.management@17.0.9/com.sun.jmx.remote.internal.ClientCommunicatorAdmin$Checker.run(ClientCommunicatorAdmin.java:180)
    // at java.base@17.0.9/java.lang.Thread.run(Thread.java:840)

    return ContainerUtil.exists(stackTrace, element -> element.getClassName().contains("com.sun.jmx.remote"));
  }

  /**
   * <a href="https://youtrack.jetbrains.com/issue/IDEA-349419/Flaky-thread-leak-in-ConsoleSpanExporter">IDEA-349419</a>
   */
  private static boolean isBuildLogCall(StackTraceElement[] stackTrace) {
    //java.lang.AssertionError: Thread leaked: Thread[#204,DefaultDispatcher-worker-6,5,main] (alive) RUNNABLE
    //  --- its stacktrace:
    //at java.base/java.io.FileOutputStream.writeBytes(Native Method)
    //at org.jetbrains.intellij.build.ConsoleSpanExporter.export(ConsoleSpanExporter.kt:44)
    //at com.intellij.platform.diagnostic.telemetry.exporters.BatchSpanProcessor$exportCurrentBatch$2.invokeSuspend(BatchSpanProcessor.kt:155)

    return ContainerUtil.exists(stackTrace, element -> element.getClassName().contains("org.jetbrains.intellij.build.ConsoleSpanExporter"));
  }

  private static CharSequence dumpThreadsToString(Map<String, Thread> after, Map<Thread, StackTraceElement[]> stackTraces) {
    StringBuilder f = new StringBuilder();
    for (Map.Entry<String, Thread> entry : after.entrySet()) {
      Thread t = entry.getValue();
      f.append('"').append(entry.getKey()).append("\" (").append(t.isAlive() ? "alive" : "dead").append(") ").append(t.getState())
        .append('\n');
      for (StackTraceElement element : stackTraces.get(t)) {
        f.append("\tat ").append(element).append('\n');
      }
      f.append('\n');
    }
    return f;
  }

  private static String internalDiagnostic(StackTraceElement[] stackTrace) {
    return stackTrace.length < 5
           ? "stackTrace.length: " + stackTrace.length
           : "(diagnostic: " +
             "0: " + stackTrace[0].getClassName() +
             " : " + stackTrace[0].getClassName().equals("sun.misc.Unsafe") +
             " . " + stackTrace[0].getMethodName() +
             " : " + stackTrace[0].getMethodName().equals("unpark") +
             " 2: " + stackTrace[2].getClassName() +
             " : " + stackTrace[2].getClassName().equals("java.util.concurrent.FutureTask") +
             " . " + stackTrace[2].getMethodName() +
             " : " + stackTrace[2].getMethodName().equals("finishCompletion") +
             ")";
  }

  private static MethodHandle getThreadsMethodHandle() {
    try {
      return MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup())
        .findStatic(Thread.class, "getThreads", MethodType.methodType(Thread[].class));
    }
    catch (Throwable e) {
      throw new IllegalStateException("Unable to access the Thread#getThreads method", e);
    }
  }

  private static void validateWhitelistedThreads(List<String> offenders) {
    List<String> sorted = new ArrayList<>(offenders);
    sorted.sort(String::compareToIgnoreCase);
    if (offenders.equals(sorted)) {
      return;
    }
    @SuppressWarnings("deprecation") String proper = String
      .join(",\n", ContainerUtil.map(sorted, s -> '"' + s + '"'))
      .replaceAll('"' + FlushingDaemon.NAME + '"', "FlushingDaemon.NAME")
      .replaceAll('"' + ProcessIOExecutorService.POOLED_THREAD_PREFIX + '"', "ProcessIOExecutorService.POOLED_THREAD_PREFIX");
    throw new AssertionError("Thread names must be sorted (for ease of maintenance). Something like this will do:\n" + proper);
  }
}
