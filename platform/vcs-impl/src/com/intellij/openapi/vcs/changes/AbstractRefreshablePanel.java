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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.Details;
import com.intellij.openapi.vcs.GenericDetailsLoader;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.Ticket;
import com.intellij.util.continuation.ModalityIgnorantBackgroundableTask;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * For presentation, which is itself in GenericDetails (not necessarily) - shown from time to time, but cached, and
 * which is a listener to some intensive changes (a group of invalidating changes should provoke a reload, but "outdated" (loaded but already not actual) results should be thrown away)
 *
 *
 * User: Irina.Chernushina
 * Date: 9/7/11
 * Time: 3:13 PM
 */
public abstract class AbstractRefreshablePanel<T> implements RefreshablePanel<Change> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.AbstractRefreshablePanel");
  protected final Ticket myTicket;
  private final DetailsPanel myDetailsPanel;
  private final GenericDetailsLoader<Ticket, T> myDetailsLoader;
  private final BackgroundTaskQueue myQueue;
  private volatile boolean myDisposed;

  protected AbstractRefreshablePanel(final Project project, final String loadingTitle, final BackgroundTaskQueue queue) {
    myQueue = queue;
    myTicket = new Ticket();
    myDetailsPanel = new DetailsPanel();
    myDetailsPanel.loading();
    myDetailsPanel.layout();
    
    myDetailsLoader = new GenericDetailsLoader<>(new Consumer<Ticket>() {
      @Override
      public void consume(Ticket ticket) {
        final Loader loader = new Loader(project, loadingTitle, myTicket.copy());
        loader.runSteadily(new Consumer<Task.Backgroundable>() {
          @Override
          public void consume(Task.Backgroundable backgroundable) {
            myQueue.run(backgroundable);
          }
        });
      }
    }, new PairConsumer<Ticket, T>() {
      @Override
      public void consume(Ticket ticket, T t) {
        acceptData(t);
      }
    });
  }

  @Override
  public boolean refreshDataSynch() {
    return false;
  }

  @CalledInAwt
  @Override
  public void dataChanged() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myTicket.increment();
  }

  @CalledInAwt
  @Override
  public void refresh() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
    if (! Comparing.equal(myDetailsLoader.getCurrentlySelected(), myTicket)) {
      final Ticket copy = myTicket.copy();
      myDetailsLoader.updateSelection(copy, false);
      myDetailsPanel.loading();
      myDetailsPanel.layout();
    } else {
      refreshPresentation();
    }
  }

  protected abstract void refreshPresentation();

  @CalledInBackground
  protected abstract T loadImpl() throws VcsException;
  @CalledInAwt
  protected abstract JPanel dataToPresentation(final T t);
  protected abstract void disposeImpl();
  
  @CalledInAwt
  private void acceptData(final T t) {
    final JPanel panel = dataToPresentation(t);
    myDetailsPanel.data(panel);
    myDetailsPanel.layout();
  }

  @Override
  public JPanel getPanel() {
    return myDetailsPanel.getPanel();
  }

  @Override
  public boolean isStillValid(Change data) {
    return true;
  }

  private class Loader extends ModalityIgnorantBackgroundableTask {
    private final Ticket myTicketCopy;
    private T myT;

    private Loader(@Nullable Project project, @NotNull String title, final Ticket ticketCopy) {
      super(project, title, false);
      myTicketCopy = ticketCopy;
    }

    @Override
    protected void doInAwtIfFail(Exception e) {
      final Exception cause;
      if (e instanceof MyRuntime && e.getCause() != null) {
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
      if (myDisposed) return;
      try {
        myDetailsLoader.take(myTicketCopy, myT);
      }
      catch (Details.AlreadyDisposedException e) {
        // t is not disposable
      }
    }

    @Override
    protected void runImpl(@NotNull ProgressIndicator indicator) {
      if (myDisposed) return;
      try {
        myT = loadImpl();
      }
      catch (VcsException e) {
        throw new MyRuntime(e);
      }
    }
  }

  private static class MyRuntime extends RuntimeException {
    private MyRuntime(Throwable cause) {
      super(cause);
    }
  }

  @Override
  public void dispose() {
    myDisposed = true;
    disposeImpl();
  }
}
