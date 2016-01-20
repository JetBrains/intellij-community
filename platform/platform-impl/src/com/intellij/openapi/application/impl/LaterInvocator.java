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
package com.intellij.openapi.application.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("SSBasedInspection")
public class LaterInvocator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.LaterInvocator");
  private static final boolean DEBUG = LOG.isDebugEnabled();

  private static final Object LOCK = new Object();
  private static final IdeEventQueue ourEventQueue = IdeEventQueue.getInstance();
  private static final FrequentEventDetector ourFrequentEventDetector = new FrequentEventDetector(1009, 100);

  private LaterInvocator() { }

  private static class RunnableInfo {
    @NotNull private final Runnable runnable;
    @NotNull private final ModalityState modalityState;
    @NotNull private final Condition<?> expired;
    @NotNull private final ActionCallback callback;

    public RunnableInfo(@NotNull Runnable runnable,
                        @NotNull ModalityState modalityState,
                        @NotNull Condition<?> expired,
                        @NotNull ActionCallback callback) {
      this.runnable = runnable;
      this.modalityState = modalityState;
      this.expired = expired;
      this.callback = callback;
    }

    @Override
    @NonNls
    public String toString() {
      return "[runnable: " + runnable + "; state=" + modalityState + (expired.value(null) ? "; expired" : "")+"] ";
    }
  }

  private static final List<Object> ourModalEntities = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final List<RunnableInfo> ourQueue = new ArrayList<RunnableInfo>(); //protected by LOCK
  private static volatile int ourQueueSkipCount; // optimization
  private static final FlushQueue ourFlushQueueRunnable = new FlushQueue();

  private static final Stack<AWTEvent> ourEventStack = new Stack<AWTEvent>(); // guarded by RUN_LOCK

  private static final EventDispatcher<ModalityStateListener> ourModalityStateMulticaster = EventDispatcher.create(ModalityStateListener.class);

  private static final List<RunnableInfo> ourForcedFlushQueue = new ArrayList<RunnableInfo>();

  public static void addModalityStateListener(@NotNull ModalityStateListener listener, @NotNull Disposable parentDisposable) {
    if (!ourModalityStateMulticaster.getListeners().contains(listener)) {
      ourModalityStateMulticaster.addListener(listener, parentDisposable);
    }
  }

  @NotNull
  static ModalityStateEx modalityStateForWindow(@NotNull Window window) {
    int index = ourModalEntities.indexOf(window);
    if (index < 0) {
      Window owner = window.getOwner();
      if (owner == null) {
        return (ModalityStateEx)ApplicationManager.getApplication().getNoneModalityState();
      }
      ModalityStateEx ownerState = modalityStateForWindow(owner);
      if (window instanceof Dialog && ((Dialog)window).isModal()) {
        return ownerState.appendEntity(window);
      }
      return ownerState;
    }

    List<Object> result = new ArrayList<Object>();
    for (Object entity : ourModalEntities) {
      if (entity instanceof Window ||
          entity instanceof ProgressIndicator && ((ProgressIndicator)entity).isModal()) {
        result.add(entity);
      }
    }
    return new ModalityStateEx(result.toArray());
  }

  @NotNull
  static ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull Condition<?> expired) {
    ModalityState modalityState = ModalityState.defaultModalityState();
    return invokeLater(runnable, modalityState, expired);
  }

  @NotNull
  static ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    return invokeLater(runnable, modalityState, Conditions.FALSE);
  }

  @NotNull
  static ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState modalityState, @NotNull Condition<?> expired) {
    ourFrequentEventDetector.eventHappened(runnable);

    final ActionCallback callback = new ActionCallback();
    RunnableInfo runnableInfo = new RunnableInfo(runnable, modalityState, expired, callback);
    synchronized (LOCK) {
      ourQueue.add(runnableInfo);
    }
    requestFlush();
    return callback;
  }

  static void invokeAndWait(@NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
    LOG.assertTrue(!isDispatchThread());

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Ref<Throwable> exception = Ref.create();
    Runnable runnable1 = new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        }
        catch (Throwable e) {
          exception.set(e);
        }
        finally {
          semaphore.up();
        }
      }

      @Override
      @NonNls
      public String toString() {
        return "InvokeAndWait[" + runnable + "]";
      }
    };
    invokeLater(runnable1, modalityState);
    semaphore.waitFor();
    if (!exception.isNull()) {
      throw new RuntimeException(exception.get());
    }
  }

  public static void enterModal(@NotNull Object modalEntity) {
    LOG.assertTrue(isDispatchThread(), "enterModal() should be invoked in event-dispatch thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + modalEntity);
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true);

    ourModalEntities.add(modalEntity);
  }

  public static void leaveModal(@NotNull Object modalEntity) {
    LOG.assertTrue(isDispatchThread(), "leaveModal() should be invoked in event-dispatch thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + modalEntity);
    }

    //noinspection StatementWithEmptyBody
    while (ourFlushQueueRunnable.runNextEvent());

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false);

    boolean removed = ourModalEntities.remove(modalEntity);
    LOG.assertTrue(removed, modalEntity);
    cleanupQueueForModal(modalEntity);
    ourQueueSkipCount = 0;
    requestFlush();
  }

  private static void cleanupQueueForModal(@NotNull final Object modalEntity) {
    synchronized (LOCK) {
      for (Iterator<RunnableInfo> iterator = ourQueue.iterator(); iterator.hasNext(); ) {
        RunnableInfo runnableInfo = iterator.next();
        if (runnableInfo.modalityState instanceof ModalityStateEx) {
          ModalityStateEx stateEx = (ModalityStateEx)runnableInfo.modalityState;
          if (stateEx.contains(modalEntity)) {
            ourForcedFlushQueue.add(runnableInfo);
            iterator.remove();
          }
        }
      }
    }
  }

  @TestOnly
  static void leaveAllModals() {
    ourModalEntities.clear();
    ourQueueSkipCount = 0;
    requestFlush();
  }

  @NotNull
  public static Object[] getCurrentModalEntities() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    //TODO!
    //LOG.assertTrue(IdeEventQueue.getInstance().isInInputEvent() || isInMyRunnable());

    return ArrayUtil.toObjectArray(ourModalEntities);
  }

  public static boolean isInModalContext() {
    LOG.assertTrue(isDispatchThread());
    return !ourModalEntities.isEmpty();
  }

  private static boolean isDispatchThread() {
    return ApplicationManager.getApplication().isDispatchThread();
  }

  private static void requestFlush() {
    if (FLUSHER_SCHEDULED.compareAndSet(false, true)) {
      SwingUtilities.invokeLater(ourFlushQueueRunnable);
    }
  }

  /**
   * There might be some requests in the queue, but ourFlushQueueRunnable might not be scheduled yet. In these circumstances 
   * {@link EventQueue#peekEvent()} default implementation would return null, and {@link UIUtil#dispatchAllInvocationEvents()} would
   * stop processing events too early and lead to spurious test failures.
   * 
   * @see IdeEventQueue#peekEvent() 
   */
  public static boolean ensureFlushRequested() {
    if (getNextEvent(false) != null) {
      SwingUtilities.invokeLater(ourFlushQueueRunnable);
      return true;
    }
    return false;
  }

  @Nullable
  private static RunnableInfo getNextEvent(boolean remove) {
    synchronized (LOCK) {
      if (!ourForcedFlushQueue.isEmpty()) {
        final RunnableInfo toRun = remove ? ourForcedFlushQueue.remove(0) : ourForcedFlushQueue.get(0);
        if (!toRun.expired.value(null)) {
          return toRun;
        }
        else {
          toRun.callback.setDone();
        }
      }


      ModalityState currentModality;
      if (ourModalEntities.isEmpty()) {
        Application application = ApplicationManager.getApplication();
        currentModality = application == null ? ModalityState.NON_MODAL : application.getNoneModalityState();
      }
      else {
        currentModality = new ModalityStateEx(ourModalEntities.toArray());
      }

      while (ourQueueSkipCount < ourQueue.size()) {
        RunnableInfo info = ourQueue.get(ourQueueSkipCount);

        if (info.expired.value(null)) {
          ourQueue.remove(ourQueueSkipCount);
          info.callback.setDone();
          continue;
        }

        if (!currentModality.dominates(info.modalityState)) {
          if (remove) {
            ourQueue.remove(ourQueueSkipCount);
          }
          return info;
        }
        ourQueueSkipCount++;
      }

      return null;
    }
  }

  private static final AtomicBoolean FLUSHER_SCHEDULED = new AtomicBoolean(false);
  private static final Object RUN_LOCK = new Object();

  private static class FlushQueue implements Runnable {
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private RunnableInfo myLastInfo;

    @Override
    public void run() {
      FLUSHER_SCHEDULED.set(false);
      if (runNextEvent()) {
        requestFlush();
      }
    }

    private boolean runNextEvent() {
      final RunnableInfo lastInfo = getNextEvent(true);
      myLastInfo = lastInfo;

      if (lastInfo != null) {
        synchronized (RUN_LOCK) { // necessary only because of switching to our own event queue
          AWTEvent event = ourEventQueue.getTrueCurrentEvent();
          ourEventStack.push(event);
          int stackSize = ourEventStack.size();

          try {
            lastInfo.runnable.run();
            lastInfo.callback.setDone();
          }
          catch (ProcessCanceledException ignored) { }
          catch (Throwable t) {
            LOG.error(t);
          }
          finally {
            LOG.assertTrue(ourEventStack.size() == stackSize);
            ourEventStack.pop();

            if (!DEBUG) myLastInfo = null;
          }
        }
      }
      return lastInfo != null;
    }

    @Override
    public String toString() {
      return "LaterInvocator.FlushQueue" + (myLastInfo == null ? "" : " lastInfo=" + myLastInfo);
    }
  }

  @TestOnly
  public static List<RunnableInfo> getLaterInvocatorQueue() {
    synchronized (LOCK) {
      return ContainerUtil.newArrayList(ourQueue);
    }
  }
}
