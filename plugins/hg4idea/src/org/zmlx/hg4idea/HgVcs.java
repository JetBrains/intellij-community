// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.zmlx.hg4idea.provider.*;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationProvider;
import org.zmlx.hg4idea.provider.commit.HgCheckinEnvironment;
import org.zmlx.hg4idea.provider.commit.HgCommitExecutor;
import org.zmlx.hg4idea.provider.update.HgIntegrateEnvironment;
import org.zmlx.hg4idea.provider.update.HgUpdateEnvironment;
import org.zmlx.hg4idea.ui.HgChangesetStatus;
import org.zmlx.hg4idea.ui.HgCurrentBranchStatus;

import javax.swing.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HgVcs extends AbstractVcs {

  public static final Topic<HgUpdater> BRANCH_TOPIC =
    new Topic<HgUpdater>("hg4idea.branch", HgUpdater.class);

  public static final Topic<HgUpdater> INCOMING_TOPIC =
    new Topic<HgUpdater>("hg4idea.incoming", HgUpdater.class);

  public static final Topic<HgUpdater> OUTGOING_TOPIC =
    new Topic<HgUpdater>("hg4idea.outgoing", HgUpdater.class);

  public static final Icon MERCURIAL_ICON = IconLoader.getIcon("/images/mercurial.png");

  private static final Icon INCOMING_ICON = IconLoader.getIcon("/actions/moveDown.png");
  private static final Icon OUTGOING_ICON = IconLoader.getIcon("/actions/moveUp.png");

  public static final String VCS_NAME = "hg4idea";

  private final HgChangeProvider changeProvider;
  private final HgProjectConfigurable configurable;
  private final HgVirtualFileListener virtualFileListener;
  private final HgRollbackEnvironment rollbackEnvironment;
  private final HgDiffProvider diffProvider;
  private final HgHistoryProvider historyProvider;
  private final HgCheckinEnvironment checkinEnvironment;
  private final HgAnnotationProvider annotationProvider;
  private final HgUpdateEnvironment updateEnvironment;
  private final HgIntegrateEnvironment integrateEnvironment;
  private final HgCachingCommitedChangesProvider commitedChangesProvider;
  private final HgCommitExecutor commitExecutor;
  private final HgCurrentBranchStatus hgCurrentBranchStatus = new HgCurrentBranchStatus();
  private final HgChangesetStatus incomingChangesStatus = new HgChangesetStatus(INCOMING_ICON);
  private final HgChangesetStatus outgoingChangesStatus = new HgChangesetStatus(OUTGOING_ICON);
  private MessageBusConnection messageBusConnection;
  private ScheduledFuture<?> changesUpdaterScheduledFuture;
  private final HgGlobalSettings globalSettings;
  private final HgProjectSettings projectSettings;

  public HgVcs(Project project,
    HgGlobalSettings globalSettings, HgProjectSettings projectSettings) {
    super(project, VCS_NAME);
    this.globalSettings = globalSettings;
    this.projectSettings = projectSettings;
    configurable = new HgProjectConfigurable(projectSettings);
    changeProvider = new HgChangeProvider(project, getKeyInstanceMethod());
    virtualFileListener = new HgVirtualFileListener(project, this);
    rollbackEnvironment = new HgRollbackEnvironment(project);
    diffProvider = new HgDiffProvider(project);
    historyProvider = new HgHistoryProvider(project);
    checkinEnvironment = new HgCheckinEnvironment(project);
    annotationProvider = new HgAnnotationProvider(project);
    updateEnvironment = new HgUpdateEnvironment(project);
    integrateEnvironment = new HgIntegrateEnvironment(project);
    commitedChangesProvider = new HgCachingCommitedChangesProvider(project);
    commitExecutor = new HgCommitExecutor(project);
  }

  public String getDisplayName() {
    return configurable.getDisplayName();
  }

  public Configurable getConfigurable() {
    return configurable;
  }

  @Override
  public ChangeProvider getChangeProvider() {
    return changeProvider;
  }

  @Override
  public RollbackEnvironment getRollbackEnvironment() {
    return rollbackEnvironment;
  }

  @Override
  public DiffProvider getDiffProvider() {
    return diffProvider;
  }

  @Override
  public VcsHistoryProvider getVcsHistoryProvider() {
    return historyProvider;
  }

  @Override
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return getVcsHistoryProvider();
  }

  @Override
  public CheckinEnvironment getCheckinEnvironment() {
    return checkinEnvironment;
  }

  @Override
  public AnnotationProvider getAnnotationProvider() {
    return annotationProvider;
  }

  @Override
  public UpdateEnvironment getUpdateEnvironment() {
    return updateEnvironment;
  }

  @Override
  public UpdateEnvironment getIntegrateEnvironment() {
    return integrateEnvironment;
  }

  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return commitedChangesProvider;
  }

  @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    VirtualFile currentDir = dir;
    while (currentDir != null) {
      if (currentDir.findFileByRelativePath(".hg") != null) {
        return true;
      }
      currentDir = currentDir.getParent();
    }
    return false;
  }

  @Override
  protected void activate() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!validateHgExecutable()) {
          return;
        }
        addListeners();
      }
    }, myProject.getDisposed());
  }

  public boolean validateHgExecutable() {
    return (new HgExecutableValidator(myProject)).check(globalSettings);
  }

  public static HgVcs getInstance(Project project) {
    return (HgVcs) ProjectLevelVcsManager.getInstance(project).findVcsByName(VCS_NAME);
  }

  public void addListeners() {
    LocalFileSystem.getInstance().addVirtualFileListener(virtualFileListener);
    ChangeListManager.getInstance(myProject).registerCommitExecutor(commitExecutor);

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar != null) {
      statusBar.addWidget(hgCurrentBranchStatus, myProject);
      statusBar.addWidget(incomingChangesStatus, myProject);
      statusBar.addWidget(outgoingChangesStatus, myProject);
    }

    final HgIncomingStatusUpdater incomingUpdater =
      new HgIncomingStatusUpdater(incomingChangesStatus, projectSettings);

    final HgOutgoingStatusUpdater outgoingUpdater =
      new HgOutgoingStatusUpdater(outgoingChangesStatus, projectSettings);

    changesUpdaterScheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(
      new Runnable() {
        public void run() {
          incomingUpdater.update(myProject);
          outgoingUpdater.update(myProject);
        }
      }, 0, globalSettings.getIncomingCheckIntervalSeconds(), TimeUnit.SECONDS);

    MessageBus messageBus = myProject.getMessageBus();
    messageBusConnection = messageBus.connect();

    messageBusConnection.subscribe(INCOMING_TOPIC, incomingUpdater);
    messageBusConnection.subscribe(OUTGOING_TOPIC, outgoingUpdater);

    messageBusConnection.subscribe(
      BRANCH_TOPIC, new HgCurrentBranchStatusUpdater(hgCurrentBranchStatus)
    );

    messageBusConnection.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      new FileEditorManagerAdapter() {
        @Override
        public void selectionChanged(FileEditorManagerEvent event) {
          Project project = event.getManager().getProject();
          project.getMessageBus()
            .asyncPublisher(BRANCH_TOPIC)
            .update(project);
        }
      }
    );
  }

  @Override
  public void deactivate() {
    LocalFileSystem.getInstance().removeVirtualFileListener(virtualFileListener);
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (messageBusConnection != null) {
      messageBusConnection.disconnect();
    }
    if (changesUpdaterScheduledFuture != null) {
      changesUpdaterScheduledFuture.cancel(true);
    }
    if (statusBar != null) {
      //statusBar.removeCustomIndicationComponent(incomingChangesStatus);
      //statusBar.removeCustomIndicationComponent(outgoingChangesStatus);
      //statusBar.removeCustomIndicationComponent(hgCurrentBranchStatus);
    }
  }

}
