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
import com.intellij.util.Consumer;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.Nullable;

import java.util.*;

abstract class GeneralRunner implements ContinuationContext {
  protected final Project myProject;
  protected final boolean myCancellable;
  protected final List<TaskDescriptor> myQueue;
  protected final Object myQueueLock;
  private boolean myTriggerSuspend;
  private ProgressIndicator myIndicator;
  private final Map<Class<? extends Exception>, Consumer<Exception>> myHandlersMap;

  GeneralRunner(final Project project, boolean cancellable) {
    myProject = project;
    myCancellable = cancellable;
    myQueueLock = new Object();
    myQueue = new LinkedList<>();
    myHandlersMap = new HashMap<>();
    myTriggerSuspend = false;
  }

  public <T extends Exception> void addExceptionHandler(final Class<T> clazz, final Consumer<T> consumer) {
    synchronized (myQueueLock) {
      myHandlersMap.put(clazz, e -> {
        if (!clazz.isAssignableFrom(e.getClass())) {
          throw new RuntimeException(e);
        }
        //noinspection unchecked
        consumer.consume((T)e);
      });
    }
  }

  protected void setIndicator(final ProgressIndicator indicator) {
    synchronized (myQueueLock) {
      myIndicator = indicator;
    }
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
      myQueue.forEach(TaskDescriptor::canceled);
      myQueue.clear();
      myIndicator = null;
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

  public abstract void ping();

  // null - no more tasks
  @Nullable
  protected TaskDescriptor getNextMatching() {
    while (true) {
      synchronized (myQueueLock) {
        if (myQueue.isEmpty()) return null;
        TaskDescriptor current = myQueue.remove(0);
        if (current.isHaveMagicCure() || myIndicator == null || !myIndicator.isCanceled()) {
          return current;
        }
      }
    }
  }

  public ProgressIndicator getIndicator() {
    synchronized (myQueueLock) {
      return myIndicator;
    }
  }
}
