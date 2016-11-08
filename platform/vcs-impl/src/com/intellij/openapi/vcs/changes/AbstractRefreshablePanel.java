/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.continuation.ModalityIgnorantBackgroundableTask;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.util.Disposer.isDisposed;

public abstract class AbstractRefreshablePanel<T> implements Disposable {
  private static final Logger LOG = Logger.getInstance(AbstractRefreshablePanel.class);

  @NotNull private final Project myProject;
  @NotNull private final String myLoadingTitle;
  private final JBLoadingPanel myDetailsPanel;
  private final BackgroundTaskQueue myQueue;

  protected AbstractRefreshablePanel(@NotNull Project project, @NotNull String loadingTitle, @NotNull BackgroundTaskQueue queue) {
    myProject = project;
    myLoadingTitle = loadingTitle;
    myQueue = queue;
    myDetailsPanel = new JBLoadingPanel(new BorderLayout(), this);
    myDetailsPanel.setLoadingText("Loading...");
  }

  @CalledInAwt
  public void refresh() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myDetailsPanel.startLoading();
    myQueue.run(new Loader(myProject, myLoadingTitle));
  }

  @CalledInBackground
  protected abstract T loadImpl() throws VcsException;
  @CalledInAwt
  protected abstract JPanel dataToPresentation(final T t);

  @CalledInAwt
  private void acceptData(final T t) {
    myDetailsPanel.add(dataToPresentation(t));
    myDetailsPanel.stopLoading();
  }

  public JPanel getPanel() {
    return myDetailsPanel;
  }

  private class Loader extends ModalityIgnorantBackgroundableTask {
    private T myData;

    private Loader(@Nullable Project project, @NotNull String title) {
      super(project, title, false);
    }

    @Override
    protected void doInAwtIfFail(@NotNull Exception e) {
      final Exception cause;
      if (e instanceof RuntimeException && e.getCause() != null) {
        cause = (Exception) e.getCause();
      } else {
        cause = e;
      }
      LOG.info(e);
      String message = cause.getMessage() == null ? e.getMessage() : cause.getMessage();
      message = message == null ? "Unknown error" : message;
      VcsBalloonProblemNotifier.showOverChangesView(myProject, message, MessageType.ERROR);
    }

    @Override
    protected void doInAwtIfCancel() {
    }

    @Override
    protected void doInAwtIfSuccess() {
      if (!isDisposed(AbstractRefreshablePanel.this)) {
        acceptData(myData);
      }
    }

    @Override
    protected void runImpl(@NotNull ProgressIndicator indicator) {
      if (!isDisposed(AbstractRefreshablePanel.this)) {
        try {
          myData = loadImpl();
        }
        catch (VcsException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public void dispose() {
  }
}