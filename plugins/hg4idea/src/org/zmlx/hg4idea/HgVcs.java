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

import com.intellij.concurrency.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.*;
import com.intellij.openapi.vcs.diff.*;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.rollback.*;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.*;
import com.intellij.util.messages.*;
import org.zmlx.hg4idea.provider.*;
import org.zmlx.hg4idea.provider.annotate.*;
import org.zmlx.hg4idea.provider.commit.*;
import org.zmlx.hg4idea.provider.update.*;
import org.zmlx.hg4idea.ui.*;

import javax.swing.*;
import java.io.File;
import java.util.concurrent.*;

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
  public static final String DIRSTATE_FILE_PATH = ".hg/dirstate";

  private final HgChangeProvider changeProvider;
  private final HgProjectConfigurable configurable;
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

  private boolean started = false;
  private HgVFSListener myVFSListener;
  private VirtualFileListener myDirStateChangeListener;

  public HgVcs(Project project,
    HgGlobalSettings globalSettings, HgProjectSettings projectSettings) {
    super(project, VCS_NAME);
    this.globalSettings = globalSettings;
    this.projectSettings = projectSettings;
    configurable = new HgProjectConfigurable(projectSettings);
    changeProvider = new HgChangeProvider(project, getKeyInstanceMethod());
    rollbackEnvironment = new HgRollbackEnvironment(project);
    diffProvider = new HgDiffProvider(project);
    historyProvider = new HgHistoryProvider(project);
    checkinEnvironment = new HgCheckinEnvironment(project);
    annotationProvider = new HgAnnotationProvider(project);
    updateEnvironment = new HgUpdateEnvironment(project);
    integrateEnvironment = new HgIntegrateEnvironment(project);
    commitedChangesProvider = new HgCachingCommitedChangesProvider(project);
    commitExecutor = new HgCommitExecutor(project);
    myDirStateChangeListener = new HgDirStateChangeListener(myProject);
  }

  public String getDisplayName() {
    return configurable.getDisplayName();
  }

  public Configurable getConfigurable() {
    return configurable;
  }

  @Override
  public ChangeProvider getChangeProvider() {
    if (!started) {
      return null;
    }

    return changeProvider;
  }

  @Override
  public RollbackEnvironment getRollbackEnvironment() {
    if (!started) {
      return null;
    }

    return rollbackEnvironment;
  }

  @Override
  public DiffProvider getDiffProvider() {
    if (!started) {
      return null;
    }

    return diffProvider;
  }

  @Override
  public VcsHistoryProvider getVcsHistoryProvider() {
    if (!started) {
      return null;
    }

    return historyProvider;
  }

  @Override
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return getVcsHistoryProvider();
  }

  @Override
  public CheckinEnvironment getCheckinEnvironment() {
    if (!started) {
      return null;
    }

    return checkinEnvironment;
  }

  @Override
  public AnnotationProvider getAnnotationProvider() {
    if (!started) {
      return null;
    }

    return annotationProvider;
  }

  @Override
  public UpdateEnvironment getUpdateEnvironment() {
    if (!started) {
      return null;
    }

    return updateEnvironment;
  }

  @Override
  public UpdateEnvironment getIntegrateEnvironment() {
    if (!started) {
      return null;
    }

    return integrateEnvironment;
  }

  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    if (!started) {
      return null;
    }
    return null;
//    return commitedChangesProvider;
  }

  @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    VirtualFile currentDir = dir;
    while (currentDir != null) {
      if (currentDir.findChild(".hg") != null) {
        return true;
      }
      currentDir = currentDir.getParent();
    }
    return false;
  }

  public boolean isStarted() {
    return started;
  }

  @Override
  protected void shutdown() throws VcsException {
    started = false;
  }

  @Override
  public void activate() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      started = true;
    } else {
      HgExecutableValidator validator = new HgExecutableValidator(myProject);
      started = validator.check(globalSettings);
    }

    if (!started) {
      return;
    }

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

    myVFSListener = new HgVFSListener(myProject, this);

    VirtualFileManager.getInstance().addVirtualFileListener(myDirStateChangeListener);
  }

  @Override
  public void deactivate() {
    if (!started) {
      return;
    }

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

    if (myVFSListener != null) {
      Disposer.dispose(myVFSListener);
      myVFSListener = null;
    }

    VirtualFileManager.getInstance().removeVirtualFileListener(myDirStateChangeListener);
  }

  public static HgVcs getInstance(Project project) {
    return (HgVcs) ProjectLevelVcsManager.getInstance(project).findVcsByName(VCS_NAME);
  }

  private static String ourTestHgExecutablePath; // path to hg in test mode

  /**
   * Sets the path to hg executable used in the test mode.
   */
  public static void setTestHgExecutablePath(String path) {
    ourTestHgExecutablePath = path;
  }

  /**
   * Returns the hg executable file.
   * If it is a test, returns the special value set in the test setup.
   * If it is a normal app, returns the value from global settings.
   */
  public String getHgExecutable() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return (new File(ourTestHgExecutablePath, SystemInfo.isWindows ? "hg.exe" : "hg")).getPath();
    }
    return globalSettings.getHgExecutable();
  }

}
