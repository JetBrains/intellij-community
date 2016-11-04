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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.continuation.ModalityIgnorantBackgroundableTask;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * For presentation, which is itself in GenericDetails (not necessarily) - shown from time to time, but cached, and
 * which is a listener to some intensive changes (a group of invalidating changes should provoke a reload, but "outdated"
 * (loaded but already not actual) results should be thrown away)
 *
 * @author Irina.Chernushina
 * @since 7.09.2011
 */
public abstract class AbstractRefreshablePanel<T> implements RefreshablePanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.AbstractRefreshablePanel");

  @NotNull private final Project myProject;
  @NotNull private final String myLoadingTitle;
  private Ticket myCurrentlySelected;
  private Ticket mySetId;
  private final Ticket myTicket;
  private final DetailsPanel myDetailsPanel;
  private final BackgroundTaskQueue myQueue;
  private volatile boolean myDisposed;

  protected AbstractRefreshablePanel(@NotNull Project project, @NotNull String loadingTitle, @NotNull BackgroundTaskQueue queue) {
    myProject = project;
    myLoadingTitle = loadingTitle;
    myQueue = queue;
    myTicket = new Ticket();
    myDetailsPanel = new DetailsPanel();
    myDetailsPanel.loading();
    myDetailsPanel.layout();
  }

  @CalledInAwt
  @Override
  public void refresh() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!Comparing.equal(myCurrentlySelected, myTicket)) {
      Ticket copy = myTicket.copy();
      Ticket previousId = myCurrentlySelected;
      myCurrentlySelected = copy;
      mySetId = null;
      if (!Comparing.equal(copy, previousId)) {
        myQueue.run(new Loader(myProject, myLoadingTitle, copy));
      }

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

  private class Loader extends ModalityIgnorantBackgroundableTask {
    private final Ticket myTicketCopy;
    private T myT;

    private Loader(@Nullable Project project, @NotNull String title, final Ticket ticketCopy) {
      super(project, title, false);
      myTicketCopy = ticketCopy;
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
      if (myDisposed) return;

      if (!myTicketCopy.equals(mySetId) && myTicketCopy.equals(myCurrentlySelected)) {
        mySetId = myTicketCopy;
        acceptData(myT);
      }
    }

    @Override
    protected void runImpl(@NotNull ProgressIndicator indicator) {
      if (myDisposed) return;
      try {
        myT = loadImpl();
      }
      catch (VcsException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void dispose() {
    myDisposed = true;
    disposeImpl();
  }

  private static class Ticket {
    private int myId;

    public Ticket() {
      myId = 0;
    }

    public Ticket(int id) {
      myId = id;
    }

    public Ticket copy() {
      return new Ticket(myId);
    }

    public void increment() {
      ++ myId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Ticket ticket = (Ticket)o;

      if (myId != ticket.myId) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myId;
    }
  }
}