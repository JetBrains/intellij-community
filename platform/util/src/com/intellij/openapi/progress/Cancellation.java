// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.DebugAttachDetectorArgs;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.JobKt;
import kotlinx.coroutines.NonCancellable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


@Internal
public final class Cancellation {

  private Cancellation() { }

  @VisibleForTesting
  public static @Nullable Job currentJob() {
    return ThreadContext.currentThreadContext().get(Job.Key);
  }

  public static void checkCancelled() {
    if (isInNonCancelableSection()) {
      return;
    }

    ensureActive();
  }

  /**
   * Ensures current {@link Job} (if any) is active, and transforms possible {@link CancellationException} thrown
   * into {@link ProcessCanceledException} -- which old java code is more ready to deal with then {@link CancellationException}
   * <br/>
   * It is <b>internal method</b>, it is made public to be called from different legacy variants of checkCancelled()
   * (from ProgressIndicator, etc.) without duplicating isInNonCancelableSection() call.
   */
  @Internal
  public static void ensureActive() {
    ThreadContext.warnAccidentalCancellation();

    Job currentJob = currentJob();
    if (currentJob != null) {
      try {
        JobKt.ensureActive(currentJob);
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (CancellationException e) {
        throw new CeProcessCanceledException(e);
      }
    }
  }

  /**
   * This flag is used only while debugging an IDE.
   *
   * @see Cancellation#initThreadNonCancellableState()
   */
  private static final ThreadLocal<DebugNonCancellableState> debugIsInNonCancelableSection = new ThreadLocal<>();


  /**
   * Obsolescence note: please do not rely on a block of code being non-cancellable, as it hinders the composability of your code.
   * For example:
   * <pre>
   * Cancellation.executeInNonCancellableSection(() -> {
   *   Cancellation.isInNonCancellableSection(); // this is true
   * })
   *
   * Cancellation.executeInNonCancellableSection(() -> {
   *   NonBlockingReadAction.executeSynchronously(() -> {
   *      Cancellation.isInNonCancellableSection(); // this is false, as cancellable read actions override the cancellation machinery
   *   })
   * })
   * </pre>
   */
  @ApiStatus.Obsolete
  public static boolean isInNonCancelableSection() {
    if (isInNonCancelableSectionInternal()) return true;
    // Avoid thread-local access when the debugger is not enabled.
    if (!DebugNonCancellableState.isDebugEnabled) return false;
    // Check whether is still attached in case debugger connection is lost before cleanup
    if (!DebugNonCancellableState.isAttached()) return false;
    DebugNonCancellableState state = debugIsInNonCancelableSection.get();
    return state != null && state.inNonCancelableSection;
  }

  private static boolean isInNonCancelableSectionInternal() {
    CoroutineContext context = ThreadContext.currentThreadContext();
    Job job = context.get(Job.Key);
    return job != null && checkIfCurrentJobIsNonCancellable(job);
  }

  private static boolean checkIfCurrentJobIsNonCancellable(@NotNull Job job) {
    // An alternative approach here is to check that `NonCancellable` is a parent of the job.
    // This would be wrong, as `NonCancellable` severs the connection of a computation to its current job, but it does not do more than that;
    // The inner computations within `NonCancellable` can still be cancellable: for example, a non-blocking read action does this.
    return job == NonCancellable.INSTANCE; // referential equality as it is a singleton
  }

  /**
   * <b>BEWARE:</b> non-cancellable sections still _could_ throw {@link ProcessCanceledException}/{@link CancellationException}
   * <br/>
   * 'Non-cancellable section' means that the computation should not react to cancellation request. But still the
   * computation can cancel itself by internal reasons -- i.e. because it meets the condition that makes it impossible
   * or useless to continue.
   * <br/>
   * Most frequent example of this: waiting for an async task. If the waiting is done in a non-cancellable section,
   * the waiting itself can't be cancelled -- but the task we're waiting for doesn't necessarily inherit non-cancellability
   * (btw, it could be started outside the non-cancellable section), so it could be cancelled, and its cancellation
   * should propagate to the waiting code -- i.e. (P)CE should be thrown from the waiting code, even if it is in a
   * non-cancellable section:
   * <pre>
   * Cancellation.computeInNonCancellableSection {
   *   ProgressManager.checkCancelled()     // Never throws (P)CE
   *   someFuture.get()                     // Can throw (P)CE, e.g. if the async task gets cancelled
   *                                        // (the lifetime of this future is not bound to the context of the current computation)
   * }
   * </pre>
   */
  public static <T, E extends Exception> T computeInNonCancelableSection(@NotNull ThrowableComputable<T, E> computable) throws E {
    if (isInNonCancelableSectionInternal()) {
      return computable.compute();
    }
    // we use a deprecated method here to handle the exception correctly
    //noinspection deprecation
    try (@NotNull AccessToken ignored = ThreadContext.installThreadContext(
      ThreadContext.currentThreadContext().plus(NonCancellable.INSTANCE), true)) {
      return computable.compute();
    }
  }

  /**
   * <b>BEWARE:</b> non-cancellable sections still _could_ throw {@link ProcessCanceledException}/{@link CancellationException}
   * -- see {@link #computeInNonCancelableSection(ThrowableComputable)} docs for details
   *
   * @see #computeInNonCancelableSection(ThrowableComputable)
   */
  public static void executeInNonCancelableSection(@NotNull Runnable runnable) {
    computeInNonCancelableSection(() -> {
      runnable.run();
      return null;
    });
  }

  public static @NotNull AccessToken withNonCancelableSection() {
    if (isInNonCancelableSectionInternal()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    return ThreadContext.installThreadContext(ThreadContext.currentThreadContext().plus(NonCancellable.INSTANCE), true);
  }

  /**
   * Throwing {@code CancellationException} from {@code <clinit>} causes {@link ExceptionInInitializerError} and bricks the class forever.
   * For example, requesting services or connecting a message bus are cancellable operations,
   * and they must not be invoked from class initializers.
   * Avoiding complex operations in initializers makes the classloading faster.
   *
   * @deprecated To prevent the new code from accidentally calling cancellable APIs,
   * we need to enable the error logging by default.
   * To do that, existing failures have to be fixed right away or suppressed.
   * This method exists to suppress existing failures in our own tests.
   * It serves as a point for finding all such cases by searching for its usages.
   * This method will throw an error in coming releases.
   * <a href="https://youtrack.jetbrains.com/issue/IJPL-1045">YouTrack issue.</a>
   */
  @Internal
  @Deprecated
  public static <T> T forceNonCancellableSectionInClassInitializer(@NotNull Supplier<T> computable) {
    return computeInNonCancelableSection(computable::get);
  }

  /**
   * Used from devkit plugin while debugging an IDE to prevent PCE throwing during stepping.
   * @return cancellability state of the current thread which can be adjusted by the debugger
   */
  @SuppressWarnings("unused")
  private static @NotNull DebugNonCancellableState initThreadNonCancellableState() {
    DebugNonCancellableState state = debugIsInNonCancelableSection.get();
    if (state != null) return state;
    state = new DebugNonCancellableState();
    debugIsInNonCancelableSection.set(state);
    return state;
  }

  /**
   * This state is extracted to a separate class so that the fields can be modified by the debugger without the need of evaluation.
   * Do not modify the names without the corresponding updates in the devkit plugin.
   */
  private static class DebugNonCancellableState {
    private static final int ATTACH_CHECK_TIMEOUT_S = 2;
    private static final boolean isDebugEnabled = DebugAttachDetectorArgs.isDebugEnabled();
    private static volatile long lastUpdateNs = System.nanoTime();
    private static volatile boolean isDebugAttached = DebugAttachDetectorArgs.isAttached();

    /**
     * This field is set to true only via debugger.
     */
    @SuppressWarnings("FieldMayBeFinal")
    private volatile boolean inNonCancelableSection = false;

    private static boolean isAttached() {
      long current = System.nanoTime();
      if (TimeUnit.NANOSECONDS.toSeconds(current - lastUpdateNs) > ATTACH_CHECK_TIMEOUT_S) {
        lastUpdateNs = current;
        isDebugAttached = DebugAttachDetectorArgs.isAttached();
      }
      return isDebugAttached;
    }
  }
}
