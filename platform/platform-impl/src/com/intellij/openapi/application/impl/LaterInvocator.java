/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"SSBasedInspection"})
public class LaterInvocator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.LaterInvocator");
  private static final boolean DEBUG = LOG.isDebugEnabled();

  public static final Object LOCK = new Object(); //public for tests
  private static final IdeEventQueue ourEventQueue = IdeEventQueue.getInstance();

  private LaterInvocator() {
  }

  private static class RunnableInfo {
    final Runnable runnable;
    final ModalityState modalityState;
    final Condition<Object> expired;
    final ActionCallback callback;

    public RunnableInfo(Runnable runnable, ModalityState modalityState, @NotNull Condition<Object> expired, @NotNull ActionCallback callback) {
      this.runnable = runnable;
      this.modalityState = modalityState;
      this.expired = expired;
      this.callback = callback;
    }

    @NonNls
    public String toString() {
      return "[runnable: " + runnable + "; state=" + modalityState + "] ";
    }
  }

  private static final List<Object> ourModalEntities = ContainerUtil.createEmptyCOWList();
  private static final List<RunnableInfo> ourQueue = new ArrayList<RunnableInfo>(); //protected by LOCK
  private static volatile int ourQueueSkipCount = 0; // optimization
  private static final Runnable ourFlushQueueRunnable = new FlushQueue();

  private static final Stack<AWTEvent> ourEventStack = new Stack<AWTEvent>();

  static boolean IS_TEST_MODE = false;

  private static final EventDispatcher<ModalityStateListener> ourModalityStateMulticaster = EventDispatcher.create(ModalityStateListener.class);


  private static final ArrayList<RunnableInfo> ourForcedFlushQueue = new ArrayList<RunnableInfo>();

  public static void addModalityStateListener(ModalityStateListener listener){
    ourModalityStateMulticaster.addListener(listener);
  }

  public static void removeModalityStateListener(ModalityStateListener listener){
    ourModalityStateMulticaster.removeListener(listener);
  }

  static ModalityStateEx modalityStateForWindow(Window window){
    int index = ourModalEntities.indexOf(window);
    if (index < 0){
      Window owner = window.getOwner();
      if (owner == null) return (ModalityStateEx)ApplicationManager.getApplication().getNoneModalityState();
      ModalityStateEx ownerState = modalityStateForWindow(owner);
      if (window instanceof Dialog && ((Dialog)window).isModal()) {
        return ownerState.appendEntity(window);
      }
      else{
        return ownerState;
      }
    }

    ArrayList<Object> result = new ArrayList<Object>();
    for (Object entity : ourModalEntities) {
      if (entity instanceof Window) {
        result.add(entity);
      }
      else if (entity instanceof ProgressIndicator) {
        if (((ProgressIndicator)entity).isModal()) {
          result.add(entity);
        }
      }
    }
    return new ModalityStateEx(result.toArray());
  }

  public static ActionCallback invokeLater(Runnable runnable) {
    return invokeLater(runnable, Conditions.FALSE);
  }

  public static ActionCallback invokeLater(Runnable runnable, @NotNull Condition expired) {
    ModalityState modalityState = ModalityState.defaultModalityState();
    return invokeLater(runnable, modalityState, expired);
  }

  public static ActionCallback invokeLater(Runnable runnable, @NotNull ModalityState modalityState) {
    return invokeLater(runnable, modalityState, Conditions.FALSE);
  }

  public static ActionCallback invokeLater(Runnable runnable, @NotNull ModalityState modalityState, @NotNull Condition<Object> expired) {
    final ActionCallback callback = new ActionCallback();
    synchronized (LOCK) {
      ourQueue.add(new RunnableInfo(runnable, modalityState, expired, callback));
    }
    requestFlush();
    return callback;
  }



  public static void invokeAndWait(final Runnable runnable, @NotNull ModalityState modalityState) {
    LOG.assertTrue(!isDispatchThread());

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    Runnable runnable1 = new Runnable() {
      public void run() {
        try {
          runnable.run();
        }
        finally {
          semaphore.up();
        }
      }

      @NonNls
      public String toString() {
        return "InvokeAndWait[" + runnable.toString() + "]";
      }
    };
    invokeLater(runnable1, modalityState);
    semaphore.waitFor();                                          
  }

  public static void enterModal(Object modalEntity) {
    if (!IS_TEST_MODE) {
      LOG.assertTrue(isDispatchThread(), "enterModal() should be invoked in event-dispatch thread");
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + modalEntity);
    }

    if (!IS_TEST_MODE) {
      ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true);
    }

    ourModalEntities.add(modalEntity);
  }

  public static void leaveModal(Object modalEntity) {
    if (!IS_TEST_MODE) {
      LOG.assertTrue(isDispatchThread(), "leaveModal() should be invoked in event-dispatch thread");
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + modalEntity);
    }

    if (!IS_TEST_MODE) {
      ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false);
    }

    boolean removed = ourModalEntities.remove(modalEntity);
    LOG.assertTrue(removed, modalEntity);
    cleanupQueueForModal(modalEntity);
    ourQueueSkipCount = 0;
    requestFlush();
  }

  private static void cleanupQueueForModal(final Object modalEntity) {
    synchronized (LOCK) {
      for (Iterator<RunnableInfo> iterator = ourQueue.iterator(); iterator.hasNext();) {
        RunnableInfo runnableInfo = iterator.next();
        if (runnableInfo.modalityState instanceof ModalityStateEx) {
          ModalityStateEx stateEx = (ModalityStateEx) runnableInfo.modalityState;
          if (stateEx.contains(modalEntity)) {
            ourForcedFlushQueue.add(runnableInfo);
            iterator.remove();
          }
        }
      }
    }
  }

  static void leaveAllModals() {
    LOG.assertTrue(IS_TEST_MODE);

    /*
    if (!IS_TEST_MODE) {
      ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged();
    }
    */

    ourModalEntities.clear();
    ourQueueSkipCount = 0;
    requestFlush();
  }

  public static Object[] getCurrentModalEntities() {
    if (!IS_TEST_MODE) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    //TODO!
    //LOG.assertTrue(IdeEventQueue.getInstance().isInInputEvent() || isInMyRunnable());

    return ArrayUtil.toObjectArray(ourModalEntities);
  }

  public static boolean isInModalContext() {
    if (!IS_TEST_MODE) {
      LOG.assertTrue(isDispatchThread());
    }
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

  @Nullable
  private static RunnableInfo pollNext() {
    synchronized (LOCK) {
      if (!ourForcedFlushQueue.isEmpty()) {
        final RunnableInfo toRun = ourForcedFlushQueue.get(0);
        ourForcedFlushQueue.remove(0);
        if (!toRun.expired.value(null)) {
          return toRun;
        } else {
          toRun.callback.setDone();
        }
      }


      ModalityStateEx currentModality = (ModalityStateEx)(ourModalEntities.isEmpty() ? ApplicationManager.getApplication()
          .getNoneModalityState() : new ModalityStateEx(ourModalEntities.toArray()));

      while(ourQueueSkipCount < ourQueue.size()){
        RunnableInfo info = ourQueue.get(ourQueueSkipCount);

        if (info.expired.value(null)) {
          ourQueue.remove(ourQueueSkipCount);
          info.callback.setDone();
          continue;
        }

        if (!currentModality.dominates(info.modalityState)) {
          ourQueue.remove(ourQueueSkipCount);
          return info;
        }
        ourQueueSkipCount++;
      }

      return null;
    }
  }

  private static final AtomicBoolean FLUSHER_SCHEDULED = new AtomicBoolean(false);
  private static final Object RUN_LOCK = new Object();

  static class FlushQueue implements Runnable {
    private RunnableInfo myLastInfo;

    public void run() {
      FLUSHER_SCHEDULED.set(false);

      final RunnableInfo lastInfo = pollNext();
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
          catch (ProcessCanceledException ex) {
            // ignore            
          }
          catch (Throwable t) {
            if (t instanceof StackOverflowError) {
              t.printStackTrace();
            }
            LOG.error(t);
          }
          finally {
            LOG.assertTrue(ourEventStack.size() == stackSize);
            ourEventStack.pop();

            if (!DEBUG) myLastInfo = null;
          }
        }

        requestFlush();
      }
    }

    @NonNls
    public String toString() {
      return "LaterInvocator[lastRunnable=" + myLastInfo + "]";
    }
  }

  @TestOnly
  public static List<Object> dumpQueue() {
    synchronized (LOCK) {
      if (!ourQueue.isEmpty()) {
        ArrayList<Object> r = new ArrayList<Object>();
        r.addAll(ourQueue);
        Collections.reverse(r);
        return r;
      }
    }
    return null;
  }
}
