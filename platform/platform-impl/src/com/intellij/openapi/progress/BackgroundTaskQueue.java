/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.openapi.progress;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Runs backgroundable tasks one by one.
 * To add a task to the queue use {@link #run(com.intellij.openapi.progress.Task.Backgroundable)}
 * BackgroundTaskQueue may have a title - this title will be used if the task which is currently running doesn't have a title.
 * 
 * @author yole
 * @author Kirill Likhodedov
 */
@SomeQueue
public class BackgroundTaskQueue implements Consumer<Task.Backgroundable> {
  private static final Logger LOG = Logger.getInstance(BackgroundTaskQueue.class.getName());
  private final Project myProject;
  private final QueueProcessor<Task.Backgroundable> myProcessor = new QueueProcessor<Task.Backgroundable>(this);
  private final String myTitle;

  public BackgroundTaskQueue(String title) {
    this(null, title);
  }

  public BackgroundTaskQueue(Project project, String title) {
    myProject = project;
    myTitle = title;
  }

  public void clear() {
    myProcessor.clear();
  }

  public void run(Task.Backgroundable task) {
    if (ApplicationManager.getApplication().isUnitTestMode()) { // test tasks are executed in this thread without the progress manager
      final EmptyProgressIndicator indicator = new EmptyProgressIndicator();
      task.run(indicator);
      if (indicator.isCanceled()) {
        task.onCancel();
      } else {
        task.onSuccess();
      }
    } else {
      myProcessor.add(task);
    }
  }

  @Override
  public void consume(final Task.Backgroundable task) {
    if (task.isConditionalModal() && !task.shouldStartInBackground()) { // modal tasks are executed synchronously
        ProgressManager.getInstance().run(task);
    } else {
      final Object LOCK = new Object();
      String title = task.getTitle();
      if (StringUtil.isEmptyOrSpaces(title)) {
        title = myTitle;
      }
      final Task.Backgroundable container = new Task.Backgroundable(myProject, title, task.isCancellable()) {
        // we wrap the task into this container to override onSuccess and onCancel to notify when the task will be completed.
        @Override
        public void onSuccess() {
          task.onSuccess();
          synchronized (LOCK) {
            LOCK.notifyAll();
          }
        }

        @Override
        public void onCancel() {
          task.onCancel();
          synchronized (LOCK) {
            LOCK.notifyAll();
          }
        }

        @Override
        public boolean shouldStartInBackground() {
          return task.shouldStartInBackground();
        }

        @Override
        public void processSentToBackground() {
          task.processSentToBackground();
        }

        @Override
        public boolean isConditionalModal() {
          return task.isConditionalModal();
        }

        @Override
        public DumbModeAction getDumbModeAction() {
          return task.getDumbModeAction();
        }

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          task.run(indicator);
        }

        @Override
        public boolean isHeadless() {
          return task.isHeadless();
        }
      };

      // start the task
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          if (myProject == null || !myProject.isDisposed()) {
            ProgressManager.getInstance().run(container);
          }
        }
      });

      // wait for task completion - next task shouldn't be started before the previous completes.
      try {
        synchronized (LOCK) {
          LOCK.wait();
        }
      } catch (InterruptedException e) {
        LOG.error(e);
      }
    }
  }

}
