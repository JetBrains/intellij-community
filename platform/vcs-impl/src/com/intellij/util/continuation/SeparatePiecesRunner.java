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
package com.intellij.util.continuation;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author irengrig
 *         Date: 4/7/11
 *         Time: 2:46 PM
 */
public class SeparatePiecesRunner extends GeneralRunner {
  private final AtomicReference<TaskWrapper> myCurrentWrapper;

  public SeparatePiecesRunner(Project project, boolean cancellable) {
    super(project, cancellable);
    myCurrentWrapper = new AtomicReference<>();
  }

  @CalledInAwt
  public void ping() {
    clearSuspend();
    final Application application = ApplicationManager.getApplication();
    if (! application.isDispatchThread()) {
      Runnable command = new Runnable() {
        public void run() {
          pingImpl();
        }
      };
      SwingUtilities.invokeLater(command);
    } else {
      pingImpl();
    }
  }

  @CalledInAwt
  private void pingImpl() {
    while (true) {
      myCurrentWrapper.set(null);
      // stop if project is being disposed
      if (!myProject.isDefault() && !myProject.isOpen()) return;
      if (getSuspendFlag()) return;
      final TaskDescriptor current = getNextMatching();
      if (current == null) {
        return;
      }

      if (Where.AWT.equals(current.getWhere())) {
        setIndicator(null);
        try {
          current.run(this);
        }
        catch (RuntimeException th) {
          handleException(th, true);
        }
      }
      else {
        final TaskWrapper task = new TaskWrapper(myProject, current.getName(), myCancellable, current);
        myCurrentWrapper.set(task);
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          setIndicator(new EmptyProgressIndicator());
        }
        else {
          setIndicator(new BackgroundableProcessIndicator(task));
        }
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, getIndicator());
        return;
      }
    }
  }

  @Override
  public void suspend() {
    super.suspend();
    final TaskWrapper wrapper = myCurrentWrapper.get();
    if (wrapper != null){
      wrapper.mySuspended = true;
    }
  }

  class TaskWrapper extends ModalityIgnorantBackgroundableTask {
    private final TaskDescriptor myTaskDescriptor;
    private volatile boolean mySuspended;

    TaskWrapper(@Nullable Project project,
                       @NotNull String title,
                       boolean canBeCancelled,
                       TaskDescriptor taskDescriptor) {
      super(project, title, canBeCancelled);
      myTaskDescriptor = taskDescriptor;
      mySuspended = false;
    }

    @Override
    protected void doInAwtIfFail(Exception e) {
      doInAwtIfCancel();
    }

    @Override
    protected void doInAwtIfCancel() {
      onCancel();
    }

    @Override
    protected void doInAwtIfSuccess() {
      if (! mySuspended) {
        ping();
      }
    }

    @Override
    protected void runImpl(@NotNull ProgressIndicator indicator) {
      myTaskDescriptor.run(SeparatePiecesRunner.this);
    }
  }
}
