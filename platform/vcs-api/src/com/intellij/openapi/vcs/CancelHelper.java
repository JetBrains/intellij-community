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
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/2/12
 * Time: 8:08 PM
 */
public class CancelHelper implements Disposable, Processor<Thread> {
  private final Map<ProgressIndicator, Thread> myMap;
  private final Set<Thread> myInterrupted;
  private MessageBusConnection myConnection;
  private final Object myLock;

  public CancelHelper(final Project project) {
    myMap = new HashMap<ProgressIndicator, Thread>();
    myLock = new Object();
    myInterrupted = new HashSet<Thread>();
    myConnection = project.getMessageBus().connect(project);
    myConnection.subscribe(ProgressManager.CANCEL_CALLED, new Runnable() {
      @Override
      public void run() {
        synchronized (myLock) {
          for (Map.Entry<ProgressIndicator, Thread> entry : myMap.entrySet()) {
            if (entry.getKey().isCanceled()) {
              myInterrupted.add(entry.getValue());
            }
          }
        }
      }
    });
  }

  public static CancelHelper getInstance(final Project project) {
    return project.getComponent(CancelHelper.class);
  }

  /**
   * @return false if process was canceled
   */
  @Override
  public boolean process(Thread thread) {
    synchronized (myLock) {
      return ! myInterrupted.contains(thread);
    }
  }

  @Override
  public void dispose() {
    myConnection.disconnect();
    myConnection = null;
  }

  public Runnable proxyRunnable(final Runnable runnable) {
    return new Runnable() {
      @Override
      public void run() {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator == null) {
          runnable.run();
          return;
        }
        try {
          synchronized (myLock) {
            myMap.put(indicator, Thread.currentThread());
          }
          runnable.run();
        } finally {
          synchronized (myLock) {
            myInterrupted.remove(Thread.currentThread());
            myMap.remove(indicator);
          }
        }
      }
    };
  }

  public Task proxyTask(final Task task) {
    if (task instanceof Task.Backgroundable) {
      return new Task.Backgroundable(task.getProject(), task.getTitle(), task.isCancellable()) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          runTask(indicator, task);
        }

        public void onCancel() {
          task.onCancel();
        }

        public void onSuccess() {
          task.onSuccess();
        }

        public String getProcessId() {
          return task.getProcessId();
        }

        @Nullable
        public NotificationInfo getNotificationInfo() {
          return task.getNotificationInfo();
        }

        @Nullable
        public NotificationInfo notifyFinished() {
          return task.notifyFinished();
        }

        public boolean isHeadless() {
          return task.isHeadless();
        }
      };
    } else {
      return new Task(task.getProject(), task.getTitle(), task.isCancellable()) {
        @Override
        public boolean isModal() {
          return task.isModal();
        }

        @Override
        public void onCancel() {
          task.onCancel();
        }

        @Override
        public void onSuccess() {
          task.onSuccess();
        }

        @Override
        public String getProcessId() {
          return task.getProcessId();
        }

        @Override
        public NotificationInfo getNotificationInfo() {
          return task.getNotificationInfo();
        }

        @Override
        public NotificationInfo notifyFinished() {
          return task.notifyFinished();
        }

        @Override
        public boolean isHeadless() {
          return task.isHeadless();
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          runTask(indicator, task);
        }
      };
    }
  }

  private void runTask(ProgressIndicator indicator, Task task) {
    try {
      synchronized (myLock) {
        myMap.put(indicator, Thread.currentThread());
      }
      task.run(indicator);
    } finally {
      synchronized (myLock) {
        myInterrupted.remove(Thread.currentThread());
        myMap.remove(indicator);
      }
    }
  }
}
