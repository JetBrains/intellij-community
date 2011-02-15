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
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.concurrency.QueueProcessor;

/**
 * Runs backgroundable tasks one by one.
 * To add a task to the queue use {@link #run(com.intellij.openapi.progress.Task.Backgroundable)}
 * BackgroundTaskQueue may have a title - this title will be used if the task which is currently running doesn't have a title.
 * 
 * @author yole
 * @author Kirill Likhodedov
 */
@SomeQueue
public class BackgroundTaskQueue {
  private static final Logger LOG = Logger.getInstance(BackgroundTaskQueue.class.getName());
  //private final Project myProject;
  private final QueueProcessor<Task.Backgroundable> myProcessor;
  private Boolean myForcedTestMode;

  public BackgroundTaskQueue(final Project project, String title) {
    final boolean headless = ApplicationManager.getApplication().isHeadlessEnvironment();
    myProcessor = new QueueProcessor<Task.Backgroundable>(headless ?
      new BackgroundableHeadlessRunner() : new BackgroundableUnderProgressRunner(title), true,
      headless ? QueueProcessor.ThreadToUse.POOLED : QueueProcessor.ThreadToUse.AWT, new Condition<Object>() {
        @Override
        public boolean value(Object o) {
          return (! ApplicationManager.getApplication().isUnitTestMode()) && (! project.isOpen()) || project.isDisposed();
        }
      });
  }

  public void clear() {
    myProcessor.clear();
  }

  public boolean isEmpty() {
    return myProcessor.isEmpty();
  }

  public void run(Task.Backgroundable task) {
    if (isTestMode()) { // test tasks are executed in this thread without the progress manager
      RunBackgroundable.runIfBackgroundThread(task, new EmptyProgressIndicator(), null);
    } else {
      myProcessor.add(task);
    }
  }

  private static class BackgroundableHeadlessRunner implements PairConsumer<Task.Backgroundable, Runnable> {
    @Override
    public void consume(Task.Backgroundable backgroundable, Runnable runnable) {
      // synchronously
      ProgressManager.getInstance().run(backgroundable);
      runnable.run();
    }
  }

  private static class BackgroundableUnderProgressRunner implements PairConsumer<Task.Backgroundable, Runnable> {
    private final String myTitle;

    public BackgroundableUnderProgressRunner(String title) {
      myTitle = title;
    }

    @Override
    public void consume(final Task.Backgroundable backgroundable, final Runnable runnable) {
      final BackgroundableProcessIndicator pi = new BackgroundableProcessIndicator(backgroundable);
      if (StringUtil.isEmptyOrSpaces(backgroundable.getTitle())) {
        pi.setTitle(myTitle);
      }
      ProgressManager.getInstance().runProcess(new Runnable() {
        @Override
        public void run() {
          // calls task's run and onCancel() or onSuccess()
          RunBackgroundable.runIfBackgroundThread(backgroundable, pi, runnable);
        }
      }, pi);
    }
  }

  private boolean isTestMode() {
    if (myForcedTestMode != null) return myForcedTestMode;
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  public void setForcedTestMode(Boolean forcedTestMode) {
    myForcedTestMode = forcedTestMode;
  }
}
