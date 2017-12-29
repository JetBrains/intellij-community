/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.process;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ProcessHandler extends UserDataHolderBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.ProcessHandler");
  /**
   * todo: replace with an overridable method [nik]
   *
   * @deprecated
   */
  public static final Key<Boolean> SILENTLY_DESTROY_ON_CLOSE = Key.create("SILENTLY_DESTROY_ON_CLOSE");
  public static final Key<Boolean> TERMINATION_REQUESTED = Key.create("TERMINATION_REQUESTED");

  private final List<ProcessListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private enum State {INITIAL, RUNNING, TERMINATING, TERMINATED}
  private final AtomicReference<State> myState = new AtomicReference<State>(State.INITIAL);

  private final Semaphore myWaitSemaphore;
  private final ProcessListener myEventMulticaster;
  private final TasksRunner myAfterStartNotifiedRunner;

  @Nullable private volatile Integer myExitCode;

  protected ProcessHandler() {
    myEventMulticaster = createEventMulticaster();
    myWaitSemaphore = new Semaphore();
    myWaitSemaphore.down();
    myAfterStartNotifiedRunner = new TasksRunner();
    myListeners.add(myAfterStartNotifiedRunner);
  }

  public void startNotify() {
    if (myState.compareAndSet(State.INITIAL, State.RUNNING)) {
      myEventMulticaster.startNotified(new ProcessEvent(this));
    }
    else {
      LOG.error("startNotify called already");
    }
  }

  protected abstract void destroyProcessImpl();

  protected abstract void detachProcessImpl();

  public abstract boolean detachIsDefault();

  /**
   * Wait for process execution.
   *
   * @return true if target process has actually ended; false if we stopped watching the process execution and don't know if it has completed.
   */
  public boolean waitFor() {
    try {
      myWaitSemaphore.waitFor();
      return true;
    }
    catch (ProcessCanceledException e) {
      return false;
    }
  }

  public boolean waitFor(long timeoutInMilliseconds) {
    try {
      return myWaitSemaphore.waitFor(timeoutInMilliseconds);
    }
    catch (ProcessCanceledException e) {
      return false;
    }
  }

  public void destroyProcess() {
    myAfterStartNotifiedRunner.execute(new Runnable() {
      @Override
      public void run() {
        if (myState.compareAndSet(State.RUNNING, State.TERMINATING)) {
          fireProcessWillTerminate(true);
          destroyProcessImpl();
        }
      }
    });
  }

  public void detachProcess() {
    myAfterStartNotifiedRunner.execute(new Runnable() {
      @Override
      public void run() {
        if (myState.compareAndSet(State.RUNNING, State.TERMINATING)) {
          fireProcessWillTerminate(false);
          detachProcessImpl();
        }
      }
    });
  }

  public boolean isProcessTerminated() {
    return myState.get() == State.TERMINATED;
  }

  public boolean isProcessTerminating() {
    return myState.get() == State.TERMINATING;
  }

  /**
   * @return exit code if the process has already finished, null otherwise
   */
  @Nullable
  public Integer getExitCode() {
    return myExitCode;
  }

  public void addProcessListener(final ProcessListener listener) {
    myListeners.add(listener);
  }

  public void addProcessListener(@NotNull final ProcessListener listener, @NotNull Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  public void removeProcessListener(final ProcessListener listener) {
    myListeners.remove(listener);
  }

  protected void notifyProcessDetached() {
    notifyTerminated(0, false);
  }

  protected void notifyProcessTerminated(final int exitCode) {
    notifyTerminated(exitCode, true);
  }

  private void notifyTerminated(final int exitCode, final boolean willBeDestroyed) {
    myAfterStartNotifiedRunner.execute(new Runnable() {
      @Override
      public void run() {
        LOG.assertTrue(isStartNotified(), "Start notify is not called");

        if (myState.compareAndSet(State.RUNNING, State.TERMINATING)) {
          try {
            fireProcessWillTerminate(willBeDestroyed);
          }
          catch (Throwable e) {
            if (!isCanceledException(e)) {
              LOG.error(e);
            }
          }
        }

        if (myState.compareAndSet(State.TERMINATING, State.TERMINATED)) {
          try {
            myExitCode = exitCode;
            myEventMulticaster.processTerminated(new ProcessEvent(ProcessHandler.this, exitCode));
          }
          catch (Throwable e) {
            if (!isCanceledException(e)) {
              LOG.error(e);
            }
          }
          finally {
            myWaitSemaphore.up();
          }
        }
      }
    });
  }

  public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
    final ProcessEvent event = new ProcessEvent(this, text);
    myEventMulticaster.onTextAvailable(event, outputType);
  }

  @Nullable
  public abstract OutputStream getProcessInput();

  private void fireProcessWillTerminate(final boolean willBeDestroyed) {
    LOG.assertTrue(isStartNotified(), "All events should be fired after startNotify is called");
    myEventMulticaster.processWillTerminate(new ProcessEvent(this), willBeDestroyed);
  }

  public boolean isStartNotified() {
    return myState.get() != State.INITIAL;
  }

  public boolean isSilentlyDestroyOnClose() {
    return false;
  }

  private ProcessListener createEventMulticaster() {
    final Class<ProcessListener> listenerClass = ProcessListener.class;
    return (ProcessListener)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, new InvocationHandler() {
      @Override
      public Object invoke(Object object, Method method, Object[] params) throws Throwable {
        for (ProcessListener listener : myListeners) {
          try {
            method.invoke(listener, params);
          }
          catch (Throwable e) {
            if (!isCanceledException(e)) {
              LOG.error(e);
            }
          }
        }
        return null;
      }
    });
  }

  private static boolean isCanceledException(Throwable e) {
    final boolean value = e instanceof InvocationTargetException && e.getCause() instanceof ProcessCanceledException;
    if (value) {
      LOG.info(e);
    }
    return value;
  }

  private final class TasksRunner extends ProcessAdapter {
    private final List<Runnable> myPendingTasks = new ArrayList<Runnable>();

    @Override
    public void startNotified(@NotNull ProcessEvent event) {
      removeProcessListener(this);
      // at this point it is guaranteed that nothing will be added to myPendingTasks
      runPendingTasks();
    }

    public void execute(@NotNull Runnable task) {
      if (isStartNotified()) {
        task.run();
      }
      else {
        synchronized (myPendingTasks) {
          myPendingTasks.add(task);
        }
        if (isStartNotified()) {
          runPendingTasks();
        }
      }
    }

    private void runPendingTasks() {
      final Runnable[] tasks;
      synchronized (myPendingTasks) {
        tasks = myPendingTasks.toArray(new Runnable[myPendingTasks.size()]);
        myPendingTasks.clear();
      }
      for (Runnable task : tasks) {
        task.run();
      }
    }
  }
}
