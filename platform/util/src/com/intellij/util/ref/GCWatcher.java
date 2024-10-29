// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ref;

import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Ref;
import com.intellij.reference.SoftReference;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.containers.ContainerUtil;
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

/**
 * A utility to garbage-collect specified objects in tests. Create a GCWatcher using {@link #tracking} or {@link #fromClearedRef}
 * and then call {@link #ensureCollected()}. Please ensure that your test doesn't hold references to objects passed to {@link #tracking},
 * so, if you pass fields or local variables there, nullify them before calling {@link #ensureCollected()}.
 *
 */
@ApiStatus.Internal
public final class GCWatcher {
  private final ReferenceQueue<Object> myQueue = new ReferenceQueue<>();
  private final Set<Reference<?>> myReferences = ContainerUtil.newConcurrentSet();

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
  public static @NotNull GCWatcher tracking(Object... objects) {
    throw new UnsupportedOperationException("Use a method with fixed number of arguments instead");
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

  private boolean isEverythingCollected() {
    while (true) {
      Reference<?> ref = myQueue.poll();
      if (ref == null) return myReferences.isEmpty();

      boolean removed = myReferences.remove(ref);
      assert removed;
    }
  }

  public boolean tryCollect(int timeoutMs) {
    return LowMemoryWatcher.runWithNotificationsSuppressed(() -> {
      long startTime = System.currentTimeMillis();
      try {
        GCUtil.allocateTonsOfMemory(new StringBuilder(), EmptyRunnable.getInstance(),
                                    () -> isEverythingCollected() || System.currentTimeMillis() - startTime > timeoutMs);
      } catch (OutOfMemoryError e) {
        // IDEA-310426
        // Ignore possible OOME that can raise during GC forcing
        // It's already been logged within GCUtil in case it appears.
      }
      return isEverythingCollected();
    });
  }

  /**
   * Attempt to run garbage collector repeatedly until all the objects passed when creating this GCWatcher are GC-ed. If that's impossible,
   * this method gives up after some time and throws {@link IllegalStateException}.
   */
  @TestOnly
  public void ensureCollected() throws IllegalStateException {
    ensureCollected(EmptyRunnable.getInstance());
  }
  /**
   * Attempt to run garbage collector repeatedly until all the objects passed when creating this GCWatcher are GC-ed. If that's impossible,
   * this method gives up after some time and throws {@link IllegalStateException}.
   */
  @TestOnly
  public void ensureCollected(@NotNull Runnable runWhileWaiting) throws IllegalStateException {
    StringBuilder log = new StringBuilder();
    if (GCUtil.allocateTonsOfMemory(log, runWhileWaiting, this::isEverythingCollected)) {
      return;
    }

    String message = "Couldn't garbage-collect some objects, they might still be reachable from GC roots: " +
                     ContainerUtil.mapNotNull(myReferences, SoftReference::dereference);

    try {
      Path file = Paths.get(System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir")), "GCWatcher.hprof.zip");
      MemoryDumpHelper.captureMemoryDumpZipped(file);

      message += "\nMemory snapshot is available at " + file + "\n";
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("##teamcity[publishArtifacts '" + file + "']");
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
