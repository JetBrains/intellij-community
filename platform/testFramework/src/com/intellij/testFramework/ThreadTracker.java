// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.io.NettyUtil;
import org.junit.Assert;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * @author cdr
 */
public class ThreadTracker {
  private static final Logger LOG = Logger.getInstance(ThreadTracker.class);
  private final Map<String, Thread> before;
  private final boolean myDefaultProjectInitialized;

  @TestOnly
  public ThreadTracker() {
    before = getThreads();
    myDefaultProjectInitialized = ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized();
  }

  private static final Method getThreads = ObjectUtils.notNull(ReflectionUtil.getDeclaredMethod(Thread.class, "getThreads"));

  @NotNull
  public static Map<String, Thread> getThreads() {
    Thread[] threads;
    try {
      // faster than Thread.getAllStackTraces().keySet()
      threads = (Thread[])getThreads.invoke(null);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return ContainerUtil.newMapFromValues(ContainerUtil.iterate(threads), Thread::getName);
  }

  // contains prefixes of the thread names which are known to be long-running (and thus exempted from the leaking threads detection)
  private static final Set<String> wellKnownOffenders = new THashSet<>();
  static {
    List<String> offenders = Arrays.asList(
    "AWT-EventQueue-",
    "AWT-Shutdown",
    "AWT-Windows",
    "Batik CleanerThread",
    "CompilerThread0",
    "External compiler",
    "Finalizer",
    FlushingDaemon.NAME,
    "IDEA Test Case Thread",
    "Image Fetcher ",
    "Java2D Disposer",
    "JobScheduler FJ pool ",
    "JPS thread pool",
    "Keep-Alive-Timer",
    "main",
    "Monitor Ctrl-Break",
    "Netty ",
    "ObjectCleanerThread",
    "Reference Handler",
    "RMI GC Daemon",
    "RMI TCP ",
    "Signal Dispatcher",
    "timer-int", //serverIm,
    "timer-sys", //clientim,
    "TimerQueue",
    "UserActivityMonitor thread",
    "VM Periodic Task Thread",
    "VM Thread",
    "YJPAgent-Telemetry"
    );
    List<String> sorted = offenders.stream().sorted(String::compareToIgnoreCase).collect(Collectors.toList());
    if (!offenders.equals(sorted)) {
      String proper = StringUtil.join(ContainerUtil.map(sorted, s -> '"' + s + '"'), ",\n").replaceAll('"'+FlushingDaemon.NAME+'"', "FlushingDaemon.NAME");
      throw new AssertionError("Thread names must be sorted (for ease of maintainance). Something like this will do:\n" + proper);
    }
    wellKnownOffenders.addAll(offenders);
    Application application = ApplicationManager.getApplication();
    // LeakHunter might be accessed first time after Application is already disposed (during test framework shutdown).
    if (application != null && !application.isDisposed()) {
      longRunningThreadCreated(application,
                               "Periodic tasks thread",
                               "ApplicationImpl pooled thread ",
                               ProcessIOExecutorService.POOLED_THREAD_PREFIX);
    }

    try {
      // init zillions of timers in e.g. MacOSXPreferencesFile
      Preferences.userRoot().flush();
    }
    catch (BackingStoreException e) {
      throw new RuntimeException(e);
    }
  }

  // marks Thread with this name as long-running, which should be ignored from the thread-leaking checks
  public static void longRunningThreadCreated(@NotNull Disposable parentDisposable, @NotNull final String... threadNamePrefixes) {
    wellKnownOffenders.addAll(Arrays.asList(threadNamePrefixes));
    Disposer.register(parentDisposable, () -> wellKnownOffenders.removeAll(Arrays.asList(threadNamePrefixes)));
  }

  @TestOnly
  public void checkLeak() throws AssertionError {
    ApplicationManager.getApplication().assertIsDispatchThread();
    NettyUtil.awaitQuiescenceOfGlobalEventExecutor(100, TimeUnit.SECONDS);
    ShutDownTracker.getInstance().waitFor(100, TimeUnit.SECONDS);
    try {
      if (myDefaultProjectInitialized != ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized()) return;

      // compare threads by name because BoundedTaskExecutor reuses application thread pool for different bounded pools, leaks of which we want to find
      Map<String, Thread> all = getThreads();
      Map<String, Thread> after = new HashMap<>(all);
      after.keySet().removeAll(before.keySet());
      Map<Thread, StackTraceElement[]> stackTraces = ContainerUtil.map2Map(after.values(), thread -> Pair.create(thread, thread.getStackTrace()));

      for (final Thread thread : after.values()) {
        if (thread == Thread.currentThread()) continue;
        ThreadGroup group = thread.getThreadGroup();
        if (group != null && "system".equals(group.getName()))continue;
        if (!thread.isAlive()) continue;

        long start = System.currentTimeMillis();
        //if (thread.isAlive()) {
        //  System.err.println("waiting for " + thread + "\n" + ThreadDumper.dumpThreadsToString());
        //}
        StackTraceElement[] traceBeforeWait = thread.getStackTrace();
        if (shouldIgnore(thread, traceBeforeWait)) continue;
        int WAIT_SEC = 10;
        StackTraceElement[] stackTrace = traceBeforeWait;
        while (System.currentTimeMillis() < start + WAIT_SEC*1_000) {
          UIUtil.dispatchAllInvocationEvents(); // give blocked thread opportunity to die if it's stuck doing invokeAndWait()
          // afters some time the submitted task can finish and the thread become idle pool
          stackTrace = thread.getStackTrace();
          if (shouldIgnore(thread, stackTrace)) break;
        }
        //long elapsed = System.currentTimeMillis() - start;
        //if (elapsed > 1_000) {
        //  System.err.println("waited for " + thread + " for " + elapsed+"ms");
        //}

        // check once more because the thread name may be set via race
        stackTraces.put(thread, stackTrace);
        if (shouldIgnore(thread, stackTrace)) continue;

        all.keySet().removeAll(after.keySet());
        Map<Thread, StackTraceElement[]> otherStackTraces = ContainerUtil.map2Map(all.values(), t -> Pair.create(t, t.getStackTrace()));

        String trace = PerformanceWatcher.printStacktrace("", thread, stackTrace);
        String traceBefore = PerformanceWatcher.printStacktrace("", thread, traceBeforeWait);

        String internalDiagnostic = stackTrace.length < 5 ? "stackTrace.length: "+stackTrace.length : "(diagnostic: " +
              "0: "+ stackTrace[0].getClassName() + " : "+ stackTrace[0].getClassName().equals("sun.misc.Unsafe")
              + " . " +stackTrace[0].getMethodName() +" : "+ stackTrace[0].getMethodName().equals("unpark")
              + " 2: "+ stackTrace[2].getClassName() +" : "+ stackTrace[2].getClassName().equals("java.util.concurrent.FutureTask")
              + " . " + stackTrace[2].getMethodName() +" : " +stackTrace[2].getMethodName().equals("finishCompletion") + ")";

        Assert.fail("Thread leaked: " +traceBefore + (trace.equals(traceBefore) ? "" : "(its trace after "+WAIT_SEC+" seconds wait:) "+trace)+
                    internalDiagnostic +
                    "\n\nLeaking threads dump:\n" + dumpThreadsToString(after, stackTraces) +
                    "\n----\nAll other threads dump:\n" + dumpThreadsToString(all, otherStackTraces));
      }
    }
    finally {
      before.clear();
    }
  }

  private static String dumpThreadsToString(Map<String, Thread> after, Map<Thread, StackTraceElement[]> stackTraces) {
    StringBuilder f = new StringBuilder();
    after.forEach((name, thread) -> {
      f.append("\"" + name + "\" (" + (thread.isAlive() ? "alive" : "dead") + ") " + thread.getState() + "\n");
      for (StackTraceElement element : stackTraces.get(thread)) {
        f.append("\tat " + element + "\n");
      }
      f.append("\n");
    });
    return f.toString();
  }

  private static boolean shouldIgnore(@NotNull Thread thread, @NotNull StackTraceElement[] stackTrace) {
    if (!thread.isAlive()) return true;
    if (isWellKnownOffender(thread.getName())) return true;

    if (stackTrace.length == 0) {
      return true; // ignore threads with empty stack traces for now. Seems they are zombies unwilling to die.
    }
    return isIdleApplicationPoolThread(stackTrace)
           || isIdleCommonPoolThread(thread, stackTrace)
           || isFutureTaskAboutToFinish(stackTrace)
           || isCoroutineSchedulerPoolThread(thread, stackTrace);
  }

  private static boolean isWellKnownOffender(@NotNull String threadName) {
    return ContainerUtil.exists(wellKnownOffenders, threadName::contains);
  }

  // true if somebody started new thread via "executeInPooledThread()" and then the thread is waiting for next task
  private static boolean isIdleApplicationPoolThread(@NotNull StackTraceElement[] stackTrace) {
    //noinspection UnnecessaryLocalVariable
    boolean insideTPEGetTask = Arrays.stream(stackTrace)
      .anyMatch(element -> element.getMethodName().equals("getTask")
                           && element.getClassName().equals("java.util.concurrent.ThreadPoolExecutor"));

    return insideTPEGetTask;
  }

  private static boolean isIdleCommonPoolThread(@NotNull Thread thread, @NotNull StackTraceElement[] stackTrace) {
    if (!ForkJoinWorkerThread.class.isAssignableFrom(thread.getClass())) {
      return false;
    }
    //noinspection UnnecessaryLocalVariable
    boolean insideAwaitWork = Arrays.stream(stackTrace)
      .anyMatch(element -> element.getMethodName().equals("awaitWork")
                           && element.getClassName().equals("java.util.concurrent.ForkJoinPool"));
    return insideAwaitWork;
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
  private static boolean isFutureTaskAboutToFinish(@NotNull StackTraceElement[] stackTrace) {
    if (stackTrace.length < 5) {
      return false;
    }

    return stackTrace[0].getClassName().equals("sun.misc.Unsafe")
      && stackTrace[0].getMethodName().equals("unpark")
      && stackTrace[2].getClassName().equals("java.util.concurrent.FutureTask")
      && stackTrace[2].getMethodName().equals("finishCompletion");
  }

  private static boolean isCoroutineSchedulerPoolThread(@NotNull Thread thread, @NotNull StackTraceElement[] stackTrace) {
    if (!"kotlinx.coroutines.scheduling.CoroutineScheduler$Worker".equals(thread.getClass().getName())) {
      return false;
    }
    //noinspection UnnecessaryLocalVariable
    boolean insideCpuWorkerIdle = Arrays.stream(stackTrace)
      .anyMatch(element -> element.getMethodName().equals("cpuWorkerIdle")
                           && element.getClassName().equals("kotlinx.coroutines.scheduling.CoroutineScheduler$Worker"));
    return insideCpuWorkerIdle;
  }

  public static void awaitJDIThreadsTermination(int timeout, @NotNull TimeUnit unit) {
    awaitThreadTerminationWithParentParentGroup("JDI main", timeout, unit);
  }
  private static void awaitThreadTerminationWithParentParentGroup(@NotNull final String grandThreadGroup,
                                                                  int timeout,
                                                                  @NotNull TimeUnit unit) {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < start + unit.toMillis(timeout)) {
      Thread jdiThread = ContainerUtil.find(getThreads().values(), thread -> {
        ThreadGroup group = thread.getThreadGroup();
        return group != null && group.getParent() != null && grandThreadGroup.equals(group.getParent().getName());
      });

      if (jdiThread == null) {
        break;
      }
      try {
        long timeLeft = start + unit.toMillis(timeout) - System.currentTimeMillis();
        LOG.debug("Waiting for the "+jdiThread+" for " + timeLeft+"ms");
        jdiThread.join(timeLeft);
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}