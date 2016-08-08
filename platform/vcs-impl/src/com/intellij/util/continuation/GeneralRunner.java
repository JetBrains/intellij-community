/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.CalledInAwt;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author irengrig
*         Date: 4/7/11
*         Time: 2:44 PM
*/
abstract class GeneralRunner implements ContinuationContext {
  protected final Project myProject;
  protected final boolean myCancellable;
  protected final List<TaskDescriptor> myQueue;
  protected final Object myQueueLock;
  private boolean myTriggerSuspend;
  private ProgressIndicator myIndicator;
  protected final Map<Object, Object> myDisasters;
  private final List<Consumer<TaskDescriptor>> myTasksPatchers;
  private final Map<Class<? extends Exception>, Consumer<Exception>> myHandlersMap;

  GeneralRunner(final Project project, boolean cancellable) {
    myProject = project;
    myCancellable = cancellable;
    myQueueLock = new Object();
    myQueue = new LinkedList<>();
    myDisasters = new HashMap<>();
    myHandlersMap = new HashMap<>();
    myTasksPatchers = new ArrayList<>();
    myTriggerSuspend = false;
  }

  public <T extends Exception> void addExceptionHandler(final Class<T> clazz, final Consumer<T> consumer) {
    synchronized (myQueueLock) {
      myHandlersMap.put(clazz, new Consumer<Exception>() {
        @Override
        public void consume(Exception e) {
          if (!clazz.isAssignableFrom(e.getClass())) {
            throw new RuntimeException(e);
          }
          //noinspection unchecked
          consumer.consume((T)e);
        }
      });
    }
  }

  protected void setIndicator(final ProgressIndicator indicator) {
    synchronized (myQueueLock) {
      myIndicator = indicator;
    }
  }

  protected void cancelIndicator() {
    synchronized (myQueueLock) {
      if (myIndicator != null){
        myIndicator.cancel();
      }
    }
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean handleException(Exception e, boolean cancelEveryThing) {
    synchronized (myQueueLock) {
      try {
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
      } finally {
        if (cancelEveryThing) {
          cancelEverything();
        }
      }
    }
    return false;
  }

  @CalledInAny
  public void cancelEverything() {
    synchronized (myQueueLock) {
      for (TaskDescriptor descriptor : myQueue) {
        descriptor.canceled();
      }
      myQueue.clear();
      myIndicator = null;
    }
  }

  public void cancelCurrent() {
    synchronized (myQueueLock) {
      if (myIndicator != null) {
        myIndicator.cancel();
      }
    }
  }

  public void suspend() {
    synchronized (myQueueLock) {
      myTriggerSuspend = true;
    }
  }

  protected boolean getSuspendFlag() {
    synchronized (myQueueLock) {
      return myTriggerSuspend;
    }
  }

  protected void clearSuspend() {
    synchronized (myQueueLock) {
      myTriggerSuspend = false;
    }
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
      final List<TaskDescriptor> asList = Arrays.asList(next);
      patchTasks(asList);
      myQueue.addAll(idx + 1, asList);
    }
  }

  private void patchTasks(final List<TaskDescriptor> next) {
    for (TaskDescriptor descriptor : next) {
      for (Consumer<TaskDescriptor> tasksPatcher : myTasksPatchers) {
        tasksPatcher.consume(descriptor);
      }
    }
  }

  @CalledInAny
  public void next(TaskDescriptor... next) {
    synchronized (myQueueLock) {
      final List<TaskDescriptor> asList = Arrays.asList(next);
      patchTasks(asList);
      myQueue.addAll(0, asList);
    }
  }

  public void next(List<TaskDescriptor> next) {
    synchronized (myQueueLock) {
      patchTasks(next);
      myQueue.addAll(0, next);
    }
  }

  @Override
  public void last(List<TaskDescriptor> next) {
    synchronized (myQueueLock) {
      patchTasks(next);
      myQueue.addAll(next);
    }
  }

  @Override
  public void last(TaskDescriptor... next) {
    synchronized (myQueueLock) {
      final List<TaskDescriptor> asList = Arrays.asList(next);
      patchTasks(asList);
      myQueue.addAll(asList);
    }
  }

  public boolean isEmpty() {
    synchronized (myQueueLock) {
      return myQueue.isEmpty();
    }
  }

  public abstract void ping();

  @Override
  public void addNewTasksPatcher(@NotNull Consumer<TaskDescriptor> consumer) {
    synchronized (myQueueLock) {
      myTasksPatchers.add(consumer);
    }
  }

  @Override
  public void removeNewTasksPatcher(@NotNull Consumer<TaskDescriptor> consumer) {
    synchronized (myQueueLock) {
      myTasksPatchers.remove(consumer);
    }
  }


  @CalledInAwt
  public void onCancel() {
    // left only "final" tasks
    synchronized (myQueueLock) {
      if (myQueue.isEmpty()) return;
      final Iterator<TaskDescriptor> iterator = myQueue.iterator();
      while (iterator.hasNext()) {
        final TaskDescriptor next = iterator.next();
        if (! next.isHaveMagicCure()) {
          iterator.remove();
        }
      }
    }

    ping();
  }

  // null - no more tasks
  @Nullable
  protected TaskDescriptor getNextMatching() {
    while (true) {
      synchronized (myQueueLock) {
        if (myQueue.isEmpty()) return null;
        TaskDescriptor current = myQueue.remove(0);
        // check if some tasks were scheduled after disaster was thrown, anyway, they should also be checked for cure
        if (! current.isHaveMagicCure()) {
          if (myIndicator != null && myIndicator.isCanceled()) {
            continue;
          } else {
            for (Map.Entry<Object, Object> entry : myDisasters.entrySet()) {
              if (! entry.getValue().equals(current.hasCure(entry.getKey()))) {
                current = null;
                break;
              }
            }
          }
        }
        if (current != null) return current;
      }
    }
  }

  public ProgressIndicator getIndicator() {
    synchronized (myQueueLock) {
      return myIndicator;
    }
  }
}
