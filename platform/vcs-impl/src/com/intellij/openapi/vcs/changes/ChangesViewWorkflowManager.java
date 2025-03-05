// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.vcs.commit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

@Service(Service.Level.PROJECT)
public final class ChangesViewWorkflowManager implements Disposable {
  @Topic.ProjectLevel
  public static final Topic<ChangesViewWorkflowListener> TOPIC =
    new Topic<>(ChangesViewWorkflowListener.class, Topic.BroadcastDirection.NONE, true);

  private final @NotNull Project myProject;

  private @Nullable ChangesViewCommitWorkflowHandler myCommitWorkflowHandler;

  private boolean myInitialized = false;

  public static @NotNull ChangesViewWorkflowManager getInstance(@NotNull Project project) {
    return project.getService(ChangesViewWorkflowManager.class);
  }

  public ChangesViewWorkflowManager(@NotNull Project project) {
    myProject = project;

    MessageBusConnection busConnection = project.getMessageBus().connect(this);
    CommitModeManager.subscribeOnCommitModeChange(busConnection, () -> updateCommitWorkflowHandler());
    ApplicationManager.getApplication().invokeLater(() -> updateCommitWorkflowHandler(), ModalityState.nonModal(), myProject.getDisposed());
  }

  public @Nullable ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    if (ApplicationManager.getApplication().isDispatchThread() && !myInitialized) {
      updateCommitWorkflowHandler();
    }
    return myCommitWorkflowHandler;
  }

  @RequiresEdt
  private void updateCommitWorkflowHandler() {
    myInitialized = true;

    boolean isNonModal = CommitModeManager.getInstance(myProject).getCurrentCommitMode() instanceof CommitMode.NonModalCommitMode;
    if (isNonModal) {
      if (myCommitWorkflowHandler == null) {
        Activity activity = StartUpMeasurer.startActivity("ChangesViewWorkflowManager initialization");

        // ChangesViewPanel can be reused between workflow instances -> should clean up after ourselves
        ChangesViewPanel changesPanel = ((ChangesViewManager)ChangesViewManager.getInstance(myProject)).initChangesPanel();
        ChangesViewCommitWorkflow workflow = new ChangesViewCommitWorkflow(myProject);
        ChangesViewCommitPanel commitPanel = new ChangesViewCommitPanel(myProject, changesPanel);
        myCommitWorkflowHandler = new ChangesViewCommitWorkflowHandler(workflow, commitPanel);

        myProject.getMessageBus().syncPublisher(TOPIC).commitWorkflowChanged();

        activity.end();
      }
      else {
        myCommitWorkflowHandler.resetActivation();
      }
    }
    else {
      if (myCommitWorkflowHandler != null) {
        Disposer.dispose(myCommitWorkflowHandler);
        myCommitWorkflowHandler = null;

        myProject.getMessageBus().syncPublisher(TOPIC).commitWorkflowChanged();
      }
    }
  }

  @Override
  public void dispose() {
    if (myCommitWorkflowHandler != null) {
      Disposer.dispose(myCommitWorkflowHandler);
      myCommitWorkflowHandler = null;
    }
  }

  public interface ChangesViewWorkflowListener extends EventListener {
    void commitWorkflowChanged();
  }
}
