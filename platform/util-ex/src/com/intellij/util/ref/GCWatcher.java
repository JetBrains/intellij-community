// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ref;

import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Ref;
import com.intellij.reference.SoftReference;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.JBR;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * A utility to garbage-collect specified objects in tests. Create a GCWatcher using {@link #tracking} or {@link #fromClearedRef}
 * and then call {@link #ensureCollected()}. Please ensure that your test doesn't hold references to objects passed to {@link #tracking},
 * so, if you pass fields or local variables there, nullify them before calling {@link #ensureCollected()}.
 *
 */
@TestOnly
@ApiStatus.Internal
public final class GCWatcher {
  private static final int NO_TIMEOUT = Integer.MAX_VALUE;
  private final ReferenceQueue<Object> myQueue = new ReferenceQueue<>();
  private final Set<Reference<?>> myReferences = ConcurrentHashMap.newKeySet();
  private boolean generateHeapDump = true;

  private GCWatcher(@NotNull Collection<?> objects) {
    for (Object o : objects) {
      if (o != null) {
        myReferences.add(new WeakReference<>(o, myQueue));
      }
    }
  }

  @Contract(pure = true)
  public static @NotNull GCWatcher tracking(Object o1) {
    return new GCWatcher(Collections.singletonList(o1));
  }

  @Contract(pure = true)
  public static @NotNull GCWatcher tracking(Object o1, Object o2) {
    return tracking(Arrays.asList(o1, o2));
  }

  @Contract(pure = true)
  public static @NotNull GCWatcher tracking(Object o1, Object o2, Object o3) {
    return tracking(Arrays.asList(o1, o2, o3));
  }

  @Contract(pure = true)
  public static @NotNull GCWatcher tracking(Object o1, Object o2, Object o3, Object o4) {
    return tracking(Arrays.asList(o1, o2, o3, o4));
  }

  @Contract(pure = true)
  public static @NotNull GCWatcher tracking(Object o1, Object o2, Object o3, Object o4, Object o5) {
    return tracking(Arrays.asList(o1, o2, o3, o4, o5));
  }

  /**
   * <p>Always throws {@link UnsupportedOperationException}.</p>
   *
   * <p>Do not use this method since vararg creates an Object
   * array that will live during the caller method.</p>
   *
   * <p>For example consider this clean and wait logic:
   *
   * <pre>
   * val watcher = tracking(lastHardLinkToAnObject)  // As it was a vararg, local variable of type
   *                                                 // Object[] is created here and is being kept
   *                                                 // until the end of the method
   * lastHardLinkToAnObject = null                   // Actually not last anymore
   * watcher.tryCollect(timout)                      // Never succeeds since vararg is still keeping a link
   * </pre>
   * </p>
   *
   * <p>Use methods with a fixed number of arguments instead.</p>
   */
  @Contract(pure = true)
  public static @NotNull GCWatcher tracking(@SuppressWarnings("unused") Object... objects) {
    throw new UnsupportedOperationException("Use .tracking() method with fixed number of arguments instead");
  }

  @Contract(pure = true)
  public static @NotNull GCWatcher tracking(@NotNull Collection<?> objects) {
    return new GCWatcher(objects);
  }

  /**
   * Create a GCWatcher from whatever is in the ref, then clear the ref.
   */
  public static @NotNull GCWatcher fromClearedRef(@NotNull Ref<?> ref) {
    GCWatcher result = tracking(ref.get());
    ref.set(null);
    return result;
  }

  public void setGenerateHeapDump(boolean generateHeapDump) {
    this.generateHeapDump = generateHeapDump;
  }

  private boolean isEverythingCollected() {
    return ContainerUtil.all(myReferences, e -> e.refersTo(null));
  }

  private void removeQueuedObjects() {
    while (true) {
      Reference<?> ref = myQueue.poll();
      if (ref == null) return;

      boolean removed = myReferences.remove(ref);
      assert removed;
    }
  }

  public boolean tryCollect(int timeoutMs) {
    return tryCollect(new StringBuilder(), timeoutMs, EmptyRunnable.getInstance());
  }

  private boolean tryCollect(StringBuilder log, long timeoutDeadline, @NotNull Runnable runWhileWaiting) {
    if (JBR.isSystemUtilsSupported()) {
      tryCollectOnJbr();
    }
    else {
      tryCollectNoJbr(log, timeoutDeadline, runWhileWaiting);
    }

    return isEverythingCollected();
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void tryCollectOnJbr() {
    assert JBR.isSystemUtilsSupported() : "JBR system utils are not supported";
    JBR.getSystemUtils().fullGC(); // JBR.fullGC also collects soft references
  }

  private void tryCollectNoJbr(StringBuilder log, long timeoutDeadline, @NotNull Runnable runWhileWaiting) {
    LowMemoryWatcher.runWithNotificationsSuppressed(() -> {
      try {
        GCUtil.allocateTonsOfMemory(log, runWhileWaiting,
                                    () -> isEverythingCollected() || System.currentTimeMillis() < timeoutDeadline);
      }
      catch (OutOfMemoryError e) {
        // IDEA-310426
        // Ignore possible OOME that can raise during GC forcing
        // It's already been logged within GCUtil in case it appears.
      }
      return null;
    });
  }

  /**
   * When runs on JBR, invokes Full GC synchronously and guarantees that soft references are also collected.
   * Otherwise, attempts to run garbage collector repeatedly until all the objects passed when creating this GCWatcher are GC-ed or
   * some timeout elapsed.
   * <p>
   * If passed objects are not collected after all the operations described above, this method throws {@link IllegalStateException}.
   * <p>
   * @see #waitForReferenceQueue()
   */
  @TestOnly
  public void ensureCollected() throws IllegalStateException {
    StringBuilder log = new StringBuilder();
    boolean collected = tryCollect(log, NO_TIMEOUT, EmptyRunnable.getInstance());
    if (!collected) {
      throwISE(log.toString());
    }
  }

  /**
   * Runs garbage collector repeatedly until all the objects passed when creating this GCWatcher are GC-ed or
   * timeout elapsed.
   * <p>
   * If passed objects are not collected after timeout, this method throws {@link IllegalStateException}.
   *
   * @see #waitForReferenceQueue()
   */
  @TestOnly
  public void ensureCollectedWithinTimeout(int timeoutMs) throws IllegalStateException {
    ensureCollectedWithinTimeout(timeoutMs, EmptyRunnable.getInstance());
  }

  /**
   * Runs garbage collector repeatedly until all the objects passed when creating this GCWatcher are GC-ed or
   * timeout elapsed.
   * <p>
   * If passed objects are not collected after timeout, this method throws {@link IllegalStateException}.
   *
   * @see #waitForReferenceQueue()
   */
  @TestOnly
  public void ensureCollectedWithinTimeout(int timeoutMs, @NotNull Runnable runWhileWaiting) throws IllegalStateException {
    StringBuilder log = new StringBuilder();
    long startTime = System.currentTimeMillis();
    long timeoutDeadline = startTime + timeoutMs;
    boolean collected = tryCollect(log, timeoutMs, runWhileWaiting);

    while (!collected && System.currentTimeMillis() < timeoutDeadline) {
      runWhileWaiting.run();
      TimeoutUtil.sleep(10); // let other threads to do some progress
      collected = tryCollect(log, timeoutDeadline, runWhileWaiting);
    }

    if (!collected) {
      throwISE(log.toString());
    }
  }

  /**
   * Waits for GC-ed objects to be picked by the {@link ReferenceQueue}, which may or may not happen at the same time
   * as objects are GC-ed (see javadoc for {@link WeakReference} class).
   */
  @TestOnly
  public void waitForReferenceQueue() {
    ensureCollected();
    removeQueuedObjects();
    for (int i = 0; i < 10_000 && !myReferences.isEmpty(); i++) {
      LockSupport.parkNanos(1_000_000);
      removeQueuedObjects();
    }
    if (!myReferences.isEmpty()) {
      throwISE("Objects were collected, but still not placed to the ReferenceQueue. This might be a bug in the GCWatcher itself.");
    }
  }

  private void throwISE(String log) {
    String message = "Couldn't garbage-collect some objects, they might still be reachable from GC roots: " +
                     ContainerUtil.mapNotNull(myReferences, SoftReference::dereference);

    try {
      if (generateHeapDump) {
        Path path = Paths.get(System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir")), "GCWatcher.hprof.zip");
        MemoryDumpHelper.captureMemoryDumpZipped(path);

        message += "\nMemory snapshot is available at " + path + "\n";
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("##teamcity[publishArtifacts '" + path + "']");
        System.out.println("##teamcity[testMetadata testName='gcwatcher' key='my log' value='" + path + "' type='artifact']");
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (isEverythingCollected()) {
      message += "\nEverything is collected after taking the heap dump.";
    }
    message += " Log:\n" + log;
    throw new IllegalStateException(message);
  }
}
