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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Continuation {
  private GeneralConsumer myGeneralConsumer;

  public Continuation(final Project project, final boolean cancellable) {
    myGeneralConsumer = new GeneralConsumer(project, cancellable);
  }

  public void run(final TaskDescriptor task) {
    if (! ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myGeneralConsumer.consume(task);
        }
      });
    } else {
      myGeneralConsumer.consume(task);
    }
  }

  private static class TaskWrapper extends Task.Backgroundable {
    private final TaskDescriptor myTaskDescriptor;
    private TaskDescriptor myResult;
    private final Consumer<TaskDescriptor> myGeneralRunner;

    private TaskWrapper(@Nullable Project project,
                       @NotNull String title,
                       boolean canBeCancelled,
                       TaskDescriptor taskDescriptor,
                       Consumer<TaskDescriptor> generalRunner) {
      super(project, title, canBeCancelled, BackgroundFromStartOption.getInstance());
      myTaskDescriptor = taskDescriptor;
      myGeneralRunner = generalRunner;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myResult = myTaskDescriptor.run();
    }

    @Override
    public void onSuccess() {
      if (myResult != null) {
        myGeneralRunner.consume(myResult);
      }
    }
  }

  private static class GeneralConsumer implements Consumer<TaskDescriptor> {
    private final Project myProject;
    private final boolean myCancellable;

    private GeneralConsumer(final Project project, boolean cancellable) {
      myProject = project;
      myCancellable = cancellable;
    }

    @CalledInAwt
    public void consume(TaskDescriptor taskDescriptor) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      TaskDescriptor current = taskDescriptor;
      while (current != null) {
        if (Where.AWT.equals(current.getWhere())) {
          current = current.run();
        } else {
          // dont forget cases here if more than 2 instances of Where
          ProgressManager.getInstance().run(new TaskWrapper(myProject, current.getName(), myCancellable, current, this));
          return;
        }
      }
    }
  }
}
