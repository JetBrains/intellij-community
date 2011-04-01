/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.continuation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CalledInAny;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.concurrency.Semaphore;
import com.sun.tools.javac.code.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Continuation {
  private GeneralRunner myGeneralRunner;

  public Continuation(final Project project, final boolean cancellable) {
    myGeneralRunner = new GeneralRunner(project, cancellable);
  }

  public void run(final TaskDescriptor... tasks) {
    if (tasks.length == 0) return;
    myGeneralRunner.next(tasks);

    pingRunnerInCorrectThread();
  }

  public void run(final List<TaskDescriptor> tasks) {
    if (tasks.isEmpty()) return;
    myGeneralRunner.next(tasks);

    pingRunnerInCorrectThread();
  }

  public void runAndWait(final TaskDescriptor... tasks) {
    runAndWait(Arrays.asList(tasks));
  }

  public void runAndWait(final List<TaskDescriptor> tasks) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    tasks.add(new TaskDescriptor("", Where.AWT) {
      @Override
      public void run(ContinuationContext context) {
        semaphore.up();
      }
    });
    run(tasks);
    semaphore.waitFor();
  }

  public <T extends Exception> void addExceptionHandler(final Class<T> clazz, final Consumer<T> consumer) {
    myGeneralRunner.addExceptionHandler(clazz, consumer);
  }

  public void runIndirect(final Consumer<ContinuationContext> consumer) {
    consumer.consume(myGeneralRunner);
    if (myGeneralRunner.isEmpty()) return;

    pingRunnerInCorrectThread();
  }

  public void resume() {
    myGeneralRunner.ping();
  }

  private void pingRunnerInCorrectThread() {
    if (! ApplicationManager.getApplication().isDispatchThread()) {
      WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
          public void run() {
            myGeneralRunner.ping();
          }
        }, null, myGeneralRunner.getProject());
    } else {
      myGeneralRunner.ping();
    }
  }

  public void clearQueue() {
    myGeneralRunner.cancelEverything();
  }

  public void cancelCurrent() {
    myGeneralRunner.cancelCurrent();
  }

  public void add(List<TaskDescriptor> list) {
    myGeneralRunner.next(list);
  }

  public boolean isEmpty() {
    return myGeneralRunner.isEmpty();
  }

  private static class TaskWrapper extends Task.Backgroundable {
    private final TaskDescriptor myTaskDescriptor;
    private final GeneralRunner myGeneralRunner;

    private TaskWrapper(@Nullable Project project,
                       @NotNull String title,
                       boolean canBeCancelled,
                       TaskDescriptor taskDescriptor,
                       GeneralRunner generalRunner) {
      super(project, title, canBeCancelled, BackgroundFromStartOption.getInstance());
      myTaskDescriptor = taskDescriptor;
      myGeneralRunner = generalRunner;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myTaskDescriptor.run(myGeneralRunner);
    }

    @Override
    public void onSuccess() {
      myGeneralRunner.ping();
    }
  }

  private static class GeneralRunner implements ContinuationContext {
    private final Project myProject;
    private final boolean myCancellable;
    private final List<TaskDescriptor> myQueue;
    private final Object myQueueLock;
    private volatile boolean myTriggerSuspend;
    private ProgressIndicator myIndicator;
    private final Map<Object, Object> myDisasters;
    private final Map<Class<? extends Exception>, Consumer<Exception>> myHandlersMap;

    private GeneralRunner(final Project project, boolean cancellable) {
      myProject = project;
      myCancellable = cancellable;
      myQueueLock = new Object();
      myQueue = new LinkedList<TaskDescriptor>();
      myDisasters = new HashMap<Object, Object>();
      myHandlersMap = new HashMap<Class<? extends Exception>, Consumer<Exception>>();
    }

    public <T extends Exception> void addExceptionHandler(final Class<T> clazz, final Consumer<T> consumer) {
      synchronized (myQueueLock) {
        myHandlersMap.put(clazz, new Consumer<Exception>() {
          @Override
          public void consume(Exception e) {
            if (! clazz.isAssignableFrom(e.getClass())) {
              throw new RuntimeException(e);
            }
            consumer.consume((T) e);
          }
        });
      }
    }

    public Project getProject() {
      return myProject;
    }

    public void clearDisasters() {
      synchronized (myQueueLock) {
        myDisasters.clear();
      }
    }

    @Override
    public boolean handleException(Exception e) {
      synchronized (myQueueLock) {
        final Class<? extends Exception> aClass = e.getClass();
        Consumer<Exception> consumer = myHandlersMap.get(e.getClass());
        if (consumer != null) {
          consumer.consume(e);
          return true;
        }
        for (Map.Entry<Class<? extends Exception>, Consumer<Exception>> entry : myHandlersMap.entrySet()) {
          if (entry.getKey().isAssignableFrom(aClass)) {
            entry.getValue().consume(e);
            return true;
          }
        }
      }
      return false;
    }

    @CalledInAny
    public void cancelEverything() {
      synchronized (myQueueLock) {
        myQueue.clear();
      }
    }

    public void cancelCurrent() {
      if (myIndicator != null) {
        myIndicator.cancel();
      }
    }

    public void suspend() {
      myTriggerSuspend = true;
    }

    @Override
    public void keepExisting(Object disaster, Object cure) {
      synchronized (myQueueLock) {
        for (TaskDescriptor taskDescriptor : myQueue) {
          taskDescriptor.addCure(disaster, cure);
        }
      }
    }

    @Override
    public void throwDisaster(@NotNull Object disaster, @NotNull final Object cure) {
      synchronized (myQueueLock) {
        final Iterator<TaskDescriptor> iterator = myQueue.iterator();
        while (iterator.hasNext()) {
          final TaskDescriptor taskDescriptor = iterator.next();
          if (taskDescriptor.isHaveMagicCure()) continue;
          final Object taskCure = taskDescriptor.hasCure(disaster);
          if (! cure.equals(taskCure)) {
            iterator.remove();
          }
        }
        myDisasters.put(disaster, cure);
      }
    }

    @Override
    public void after(@NotNull TaskDescriptor inQueue, TaskDescriptor... next) {
      synchronized (myQueueLock) {
        int idx = -1;
        int i = 0;
        for (TaskDescriptor descriptor : myQueue) {
          if (descriptor == inQueue) {
            idx = i;
            break;
          }
          ++ i;
        }
        assert idx != -1;
        myQueue.addAll(idx + 1, Arrays.asList(next));
      }
    }

    @CalledInAny
    public void next(TaskDescriptor... next) {
      synchronized (myQueueLock) {
        myQueue.addAll(0, Arrays.asList(next));
      }
    }

    public void next(List<TaskDescriptor> next) {
      synchronized (myQueueLock) {
        myQueue.addAll(0, next);
      }
    }

    @Override
    public void last(List<TaskDescriptor> next) {
      synchronized (myQueueLock) {
        myQueue.addAll(next);
      }
    }

    @Override
    public void last(TaskDescriptor... next) {
      synchronized (myQueueLock) {
        myQueue.addAll(Arrays.asList(next));
      }
    }

    public boolean isEmpty() {
      synchronized (myQueueLock) {
        return myQueue.isEmpty();
      }
    }

    @CalledInAwt
    public void ping() {
      ApplicationManager.getApplication().assertIsDispatchThread();

      while (true) {
      // stop if project is being disposed
        if (! myProject.isOpen()) return;

        TaskDescriptor current;
        synchronized (myQueueLock) {
          if (myQueue.isEmpty()) return;
          if (myTriggerSuspend) {
            myTriggerSuspend = false;
            return;
          }
          current = myQueue.remove(0);
          // check if some tasks were scheduled after disaster was thrown, anyway, they should also be checked for cure
          if (! current.isHaveMagicCure()) {
            for (Map.Entry<Object, Object> entry : myDisasters.entrySet()) {
              if (! entry.getValue().equals(current.hasCure(entry.getKey()))) {
                current = null;
                break;
              }
            }
          }
          if (current == null) continue;
        }

        if (Where.AWT.equals(current.getWhere())) {
          myIndicator = null;
          current.run(this);
        } else {
          final TaskWrapper task = new TaskWrapper(myProject, current.getName(), myCancellable, current, this);
          myIndicator = ApplicationManager.getApplication().isUnitTestMode() ? new EmptyProgressIndicator() :
                        new BackgroundableProcessIndicator(task);
          ProgressManagerImpl.runProcessWithProgressAsynchronously(task, myIndicator);
          return;
        }
      }
    }
  }
}
