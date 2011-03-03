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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CalledInAny;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myGeneralRunner.ping();
        }
      });
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
    private boolean myTriggerSuspend;
    private BackgroundableProcessIndicator myIndicator;

    private GeneralRunner(final Project project, boolean cancellable) {
      myProject = project;
      myCancellable = cancellable;
      myQueue = Collections.synchronizedList(new LinkedList<TaskDescriptor>());
    }

    @CalledInAny
    public void cancelEverything() {
      myQueue.clear();
    }

    public void cancelCurrent() {
      if (myIndicator != null) {
        myIndicator.cancel();
      }
    }

    public void suspend() {
      myTriggerSuspend = true;
    }

    @CalledInAny
    public void next(TaskDescriptor... next) {
      myQueue.addAll(0, Arrays.asList(next));
    }

    public void next(List<TaskDescriptor> next) {
      myQueue.addAll(0, next);
    }

    @Override
    public void last(List<TaskDescriptor> next) {
      myQueue.addAll(next);
    }

    @Override
    public void last(TaskDescriptor... next) {
      myQueue.addAll(Arrays.asList(next));
    }

    public boolean isEmpty() {
      return myQueue.isEmpty();
    }

    @CalledInAwt
    public void ping() {
      ApplicationManager.getApplication().assertIsDispatchThread();

      while (true) {
      // stop if project is being disposed
        if (! myProject.isOpen()) return;
        if (myQueue.isEmpty()) return;
        if (myTriggerSuspend) {
          myTriggerSuspend = false;
          return;
        }
        TaskDescriptor current = myQueue.remove(0);

        if (Where.AWT.equals(current.getWhere())) {
          myIndicator = null;
          current.run(this);
        } else {
          final TaskWrapper task = new TaskWrapper(myProject, current.getName(), myCancellable, current, this);
          myIndicator = new BackgroundableProcessIndicator(task);
          ProgressManagerImpl.runProcessWithProgressAsynchronously(task, myIndicator);
          return;
        }
      }
    }
  }
}
