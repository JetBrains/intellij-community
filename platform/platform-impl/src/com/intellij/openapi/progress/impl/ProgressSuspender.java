// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorListener;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException;

@ApiStatus.Internal
public final class ProgressSuspender implements AutoCloseable {
  @Topic.AppLevel
  public static final Topic<SuspenderListener> TOPIC = new Topic<>("ProgressSuspender", SuspenderListener.class, Topic.BroadcastDirection.NONE);

  private final Object myLock = new Object();
  private static final Application ourApp = ApplicationManager.getApplication();
  private final @NotNull @NlsContexts.ProgressText String mySuspendedText;
  private @Nullable @NlsContexts.ProgressText String myTempReason;
  private final SuspenderListener myPublisher;
  private volatile boolean mySuspended;
  private final CoreProgressManager.CheckCanceledHook myHook = this::freezeIfNeeded;
  private final Set<ProgressIndicator> myProgresses = ConcurrentCollectionFactory.createConcurrentSet();
  private final Map<ProgressIndicator, Integer> myProgressesInNonSuspendableSections = new ConcurrentHashMap<>();
  private boolean myClosed;

  private static final Map<ProgressIndicator, ProgressSuspender> ourProgressToSuspenderMap = new ConcurrentHashMap<>();

  private ProgressSuspender(@NotNull ProgressIndicator progress, @NotNull @NlsContexts.ProgressText String suspendedText) {
    mySuspendedText = suspendedText;
    assert progress.isRunning();
    assert ProgressIndicatorProvider.getGlobalProgressIndicator() == progress;
    myPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC);

    attachToProgress(progress);

    if (progress instanceof ProgressIndicatorEx indicatorEx) {
      new ProgressIndicatorListener() {
        @Override
        public void cancelled() {
          resumeProcess();
        }
      }.installToProgress(indicatorEx);
    }

    myPublisher.suspendableProgressAppeared(this);
  }

  @Override
  public void close() {
    synchronized (myLock) {
      myClosed = true;
      mySuspended = false;
      ((ProgressManagerImpl)ProgressManager.getInstance()).removeCheckCanceledHook(myHook);
    }
    for (ProgressIndicator progress : myProgresses) {
      ourProgressToSuspenderMap.remove(progress);
      myPublisher.suspendableProgressRemoved(this, progress);
    }
  }

  public static @NotNull ProgressSuspender markSuspendable(@NotNull ProgressIndicator indicator, @NotNull @NlsContexts.ProgressText String suspendedText) {
    return new ProgressSuspender(indicator, suspendedText);
  }

  public void executeNonSuspendableSection(@NotNull ProgressIndicator indicator, @NotNull Runnable runnable) {
    myProgressesInNonSuspendableSections.compute(indicator, (_, number) -> (number == null ? 0 : number) + 1);
    try {
      runnable.run();
    }
    finally {
      myProgressesInNonSuspendableSections.compute(indicator, (_, number) -> number == null || number <= 1 ? null : number - 1);
    }
  }

  public static ProgressSuspender getSuspender(@NotNull ProgressIndicator indicator) {
    return ourProgressToSuspenderMap.get(indicator);
  }

  /**
   * Associates an additional progress indicator with this suspender, so that its {@code #checkCanceled} can later block the calling thread.
   */
  public void attachToProgress(@NotNull ProgressIndicator progress) {
    myProgresses.add(progress);
    ourProgressToSuspenderMap.put(progress, this);
    myPublisher.suspendableProgressAppeared(this, progress);
  }

  public @NotNull @NlsContexts.ProgressText String getSuspendedText() {
    synchronized (myLock) {
      return myTempReason != null ? myTempReason : mySuspendedText;
    }
  }

  public boolean isSuspended() {
    return mySuspended;
  }

  public boolean isClosed() {
    return myClosed;
  }

  /**
   * @param reason if provided, is displayed in the UI instead of suspended text passed into constructor until the progress is resumed
   */
  public void suspendProcess(@NlsContexts.ProgressText @Nullable String reason) {
    synchronized (myLock) {
      if (mySuspended || myClosed) return;

      mySuspended = true;
      myTempReason = reason;

      ((ProgressManagerImpl)ProgressManager.getInstance()).addCheckCanceledHook(myHook);
    }

    myPublisher.suspendedStatusChanged(this);

    // Give running NonBlockingReadActions a chance to suspend
    ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> {}));
  }

  public void resumeProcess() {
    synchronized (myLock) {
      if (!mySuspended) return;

      mySuspended = false;
      myTempReason = null;

      ((ProgressManagerImpl)ProgressManager.getInstance()).removeCheckCanceledHook(myHook);

      myLock.notifyAll();
    }

    myPublisher.suspendedStatusChanged(this);
  }

  private boolean freezeIfNeeded(ProgressIndicator current) {
    if (current == null) {
      current = ProgressIndicatorProvider.getGlobalProgressIndicator();
    }
    if (current == null || !myProgresses.contains(current)) {
      return false;
    }

    if (myProgressesInNonSuspendableSections.containsKey(current)) {
      return false;
    }

    if (isCurrentThreadHoldingKnownLocks()) {
      return false;
    }

    synchronized (myLock) {
      while (mySuspended) {
        try {
          myLock.wait(10000);
        }
        catch (InterruptedException ignore) {
        }
      }

      return true;
    }
  }

  private static boolean isCurrentThreadHoldingKnownLocks() {
    if (ourApp.isReadAccessAllowed()) {
      return true;
    }

    var thread = Thread.currentThread();
    if (thread.isVirtual()) {
      return VirtualThreadMonitors.ownsMonitors(thread);
    }
    ThreadInfo[] infos = ManagementFactory.getThreadMXBean().getThreadInfo(new long[]{thread.threadId()}, true, false);
    return infos.length > 0 && infos[0] != null && infos[0].getLockedMonitors().length > 0;
  }

  private static final class VirtualThreadMonitors {
    private static final @Nullable MethodHandle OWNS_MONITORS = findOwnsMonitors();

    private static @Nullable MethodHandle findOwnsMonitors() {
      try {
        var snapshotClass = Class.forName("jdk.internal.vm.ThreadSnapshot");
        var snapshot = snapshotClass.getDeclaredMethod("of", Thread.class);
        var ownsMonitors = snapshotClass.getDeclaredMethod("ownsMonitors");
        snapshot.setAccessible(true);
        ownsMonitors.setAccessible(true);
        var lookup = MethodHandles.lookup();
        return MethodHandles.filterReturnValue(lookup.unreflect(snapshot), lookup.unreflect(ownsMonitors));
      }
      catch (ReflectiveOperationException | RuntimeException e) {
        rethrowControlFlowException(e);
        return null;
      }
    }

    private static boolean ownsMonitors(Thread thread) {
      if (OWNS_MONITORS != null) {
        try {
          return (boolean)OWNS_MONITORS.invokeExact(thread);
        }
        catch (Throwable e) {
          rethrowControlFlowException(e);
        }
      }
      // Suspend only when the runtime confirms that the thread holds no monitors.
      return true;
    }
  }

  public interface SuspenderListener {
    /** Called (on any thread) when a new suspender is created */
    default void suspendableProgressAppeared(@NotNull ProgressSuspender suspender) {}

    /** Called (on any thread) when a suspender (might already exist) is attached to a new progress */
    default void suspendableProgressAppeared(@NotNull ProgressSuspender suspender, @NotNull ProgressIndicator progressIndicator) {}

    /** Called (on any thread) when a suspender is destroyed */
    default void suspendableProgressRemoved(@NotNull ProgressSuspender suspender, @NotNull ProgressIndicator progressIndicator) {}

    /** Called (on any thread) when a progress is suspended or resumed */
    default void suspendedStatusChanged(@NotNull ProgressSuspender suspender) {}
  }

}
