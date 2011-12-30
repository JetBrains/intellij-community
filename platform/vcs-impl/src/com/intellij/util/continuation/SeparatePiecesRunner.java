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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author irengrig
 *         Date: 4/7/11
 *         Time: 2:46 PM
 */
public class SeparatePiecesRunner extends GeneralRunner {
  public SeparatePiecesRunner(Project project, boolean cancellable) {
    super(project, cancellable);
  }

  @CalledInAwt
  public void ping() {
    clearSuspend();
    if (! ApplicationManager.getApplication().isDispatchThread()) {
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
    // stop if project is being disposed
      if (! myProject.isDefault() && ! myProject.isOpen()) return;
      if (getSuspendFlag()) return;
      final TaskDescriptor current = getNextMatching();
      if (current == null) {
        return;
      }

      if (Where.AWT.equals(current.getWhere())) {
        myIndicator = null;
        current.run(this);
      } else {
        final TaskWrapper task = new TaskWrapper(myProject, current.getName(), myCancellable, current, this);
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          myIndicator = new EmptyProgressIndicator();
        }
        else {
          myIndicator = new BackgroundableProcessIndicator(task);
          // a hack to set current modality
          //((BackgroundableProcessIndicator) myIndicator).setModalityProgress(null);
        }
        ProgressManagerImpl.runProcessWithProgressAsynchronously(task, myIndicator);
        return;
      }
    }
  }

  static class TaskWrapper extends ModalityIgnorantBackgroundableTask {
    private final TaskDescriptor myTaskDescriptor;
    private final GeneralRunner myGeneralRunner;

    TaskWrapper(@Nullable Project project,
                       @NotNull String title,
                       boolean canBeCancelled,
                       TaskDescriptor taskDescriptor,
                       GeneralRunner generalRunner) {
      super(project, title, canBeCancelled, BackgroundFromStartOption.getInstance());
      myTaskDescriptor = taskDescriptor;
      myGeneralRunner = generalRunner;
    }

    @Override
    protected void doInAwtIfFail(Exception e) {
      doInAwtIfCancel();
    }

    @Override
    protected void doInAwtIfCancel() {
      myGeneralRunner.onCancel();
    }

    @Override
    protected void doInAwtIfSuccess() {
      myGeneralRunner.ping();
    }

    @Override
    protected void runImpl(@NotNull ProgressIndicator indicator) {
      myTaskDescriptor.run(myGeneralRunner);
    }
  }
}
