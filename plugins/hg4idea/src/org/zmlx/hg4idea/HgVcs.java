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
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RepositoryChangeListener;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.containers.ComparatorDelegate;
import com.intellij.util.containers.Convertor;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.provider.*;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationProvider;
import org.zmlx.hg4idea.provider.commit.HgCheckinEnvironment;
import org.zmlx.hg4idea.provider.commit.HgCommitAndPushExecutor;
import org.zmlx.hg4idea.provider.update.HgIntegrateEnvironment;
import org.zmlx.hg4idea.provider.update.HgUpdateEnvironment;
import org.zmlx.hg4idea.ui.HgChangesetStatus;
import org.zmlx.hg4idea.ui.HgCurrentBranchStatus;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HgVcs extends AbstractVcs<CommittedChangeList> {

  public static final Topic<HgUpdater> BRANCH_TOPIC =
    new Topic<HgUpdater>("hg4idea.branch", HgUpdater.class);

  public static final Topic<HgUpdater> REMOTE_TOPIC =
    new Topic<HgUpdater>("hg4idea.remote", HgUpdater.class);

  private static final Icon INCOMING_ICON = IconLoader.getIcon("/actions/moveDown.png");
  private static final Icon OUTGOING_ICON = IconLoader.getIcon("/actions/moveUp.png");
  private static final Logger LOG = Logger.getInstance(HgVcs.class);

  public static final String VCS_NAME = "hg4idea";
  public static final String NOTIFICATION_GROUP_ID = "Mercurial";
  public static final String HG_EXECUTABLE_FILE_NAME = (SystemInfo.isWindows ? "hg.exe" : "hg");

  private static final String ORIG_FILE_PATTERN = "*.orig";

  private final HgChangeProvider changeProvider;
  private final HgRollbackEnvironment rollbackEnvironment;
  private final HgDiffProvider diffProvider;
  private final HgHistoryProvider historyProvider;
  private final HgCheckinEnvironment checkinEnvironment;
  private final HgAnnotationProvider annotationProvider;
  private final HgUpdateEnvironment updateEnvironment;
  private final HgIntegrateEnvironment integrateEnvironment;
  private final HgCachingCommitedChangesProvider commitedChangesProvider;
  private final HgCurrentBranchStatus hgCurrentBranchStatus = new HgCurrentBranchStatus();
  private final HgChangesetStatus incomingChangesStatus = new HgChangesetStatus(INCOMING_ICON, "Incoming");
  private final HgChangesetStatus outgoingChangesStatus = new HgChangesetStatus(OUTGOING_ICON, "Outgoing");
  private MessageBusConnection messageBusConnection;
  private ScheduledFuture<?> changesUpdaterScheduledFuture;
  private final HgGlobalSettings globalSettings;
  private final HgProjectSettings projectSettings;
  private final ProjectLevelVcsManager myVcsManager;

  private HgVFSListener myVFSListener;
  private RepositoryChangeListener myDirStateChangeListener;
  private final HgMergeProvider myMergeProvider;
  private HgExecutableValidator myExecutableValidator;
  private final Object myExecutableValidatorLock = new Object();
  private File myPromptHooksExtensionFile;
  private CommitExecutor myCommitAndPushExecutor;

  public HgVcs(Project project,
    HgGlobalSettings globalSettings, HgProjectSettings projectSettings,
    ProjectLevelVcsManager vcsManager) {
    super(project, VCS_NAME);
    this.globalSettings = globalSettings;
    this.projectSettings = projectSettings;
    myVcsManager = vcsManager;
    changeProvider = new HgChangeProvider(project, getKeyInstanceMethod());
    rollbackEnvironment = new HgRollbackEnvironment(project);
    diffProvider = new HgDiffProvider(project);
    historyProvider = new HgHistoryProvider(project);
    checkinEnvironment = new HgCheckinEnvironment(project);
    annotationProvider = new HgAnnotationProvider(project);
    updateEnvironment = new HgUpdateEnvironment(project);
    integrateEnvironment = new HgIntegrateEnvironment(project);
    commitedChangesProvider = new HgCachingCommitedChangesProvider(project);
    myDirStateChangeListener = new RepositoryChangeListener(myProject, ".hg/dirstate");
    myMergeProvider = new HgMergeProvider(myProject);
    myCommitAndPushExecutor = new HgCommitAndPushExecutor(checkinEnvironment);
  }

  public String getDisplayName() {
    return HgVcsMessages.message("hg4idea.mercurial");
  }

  public Configurable getConfigurable() {
    return new HgProjectConfigurable(projectSettings);
  }

  public HgProjectSettings getProjectSettings() {
    return projectSettings;
  }

  @Override
  public ChangeProvider getChangeProvider() {
    return changeProvider;
  }

  @Nullable
  @Override
  public RollbackEnvironment createRollbackEnvironment() {
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

  @Nullable
  @Override
  public CheckinEnvironment createCheckinEnvironment() {
    return checkinEnvironment;
  }

  @Override
  public AnnotationProvider getAnnotationProvider() {
    return annotationProvider;
  }

  @Override
  public MergeProvider getMergeProvider() {
    return myMergeProvider;
  }

  @Nullable
  @Override
  public UpdateEnvironment createUpdateEnvironment() {
    return updateEnvironment;
  }

  @Override
  public UpdateEnvironment getIntegrateEnvironment() {
    return integrateEnvironment;
  }

  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return null;
//    return commitedChangesProvider;
  }

  @Override
  public boolean allowsNestedRoots() {
    return true;
  }

  @Override
  public <S> List<S> filterUniqueRoots(final List<S> in, final Convertor<S, VirtualFile> convertor) {
    Collections.sort(in, new ComparatorDelegate<S, VirtualFile>(convertor, FilePathComparator.getInstance()));

    for (int i = 1; i < in.size(); i++) {
      final S sChild = in.get(i);
      final VirtualFile child = convertor.convert(sChild);
      final VirtualFile childRoot = HgUtil.getHgRootOrNull(myProject, child);
      if (childRoot == null) {
        continue;
      }
      for (int j = i - 1; j >= 0; --j) {
        final S sParent = in.get(j);
        final VirtualFile parent = convertor.convert(sParent);
        // if the parent is an ancestor of the child and that they share common root, the child is removed
        if (VfsUtil.isAncestor(parent, child, false) && VfsUtil.isAncestor(childRoot, parent, false)) {
          in.remove(i);
          //noinspection AssignmentToForLoopParameter
          --i;
          break;
        }
      }
    }
    return in;
  }


  @Override
  public RootsConvertor getCustomConvertor() {
    return HgRootsHandler.getInstance(myProject);
  }

    @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    return HgUtil.getNearestHgRoot(dir) != null;
  }

  /**
   * @return the prompthooks.py extension used for capturing prompts from Mercurial and requesting IDEA's user about authentication.
   */
  public @NotNull File getPromptHooksExtensionFile() {
    if (myPromptHooksExtensionFile == null) {
      // check that hooks are available
      myPromptHooksExtensionFile = HgUtil.getTemporaryPythonFile("prompthooks");
      if (myPromptHooksExtensionFile == null || !myPromptHooksExtensionFile.exists()) {
        LOG.error("prompthooks.py Mercurial extension is not found. Please reinstall " + ApplicationNamesInfo.getInstance().getProductName());
      }
    }
    return myPromptHooksExtensionFile;
  }

  @Override
  public void activate() {
    // validate hg executable on start
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getExecutableValidator().checkExecutableAndShowDialogIfNeeded();
    }

    // status bar
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar != null) {
      statusBar.addWidget(hgCurrentBranchStatus, myProject);
      statusBar.addWidget(incomingChangesStatus, myProject);
      statusBar.addWidget(outgoingChangesStatus, myProject);
    }

    // updaters and listeners
    final HgRemoteStatusUpdater remoteUpdater = new HgRemoteStatusUpdater(this, incomingChangesStatus, outgoingChangesStatus, projectSettings);
    changesUpdaterScheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(
      new Runnable() {
        public void run() {
          remoteUpdater.update(myProject);
        }
      }, 0, HgGlobalSettings.getIncomingCheckIntervalSeconds(), TimeUnit.SECONDS);

    messageBusConnection = myProject.getMessageBus().connect();
    messageBusConnection.subscribe(REMOTE_TOPIC, remoteUpdater);
    messageBusConnection.subscribe(BRANCH_TOPIC, new HgCurrentBranchStatusUpdater(hgCurrentBranchStatus));
    messageBusConnection.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      new FileEditorManagerAdapter() {
        @Override
        public void selectionChanged(FileEditorManagerEvent event) {
          Project project = event.getManager().getProject();
          project.getMessageBus()
            .syncPublisher(BRANCH_TOPIC)
            .update(project);
        }
      }
    );

    myVFSListener = new HgVFSListener(myProject, this);
    myDirStateChangeListener.activate();

    // ignore temporary files
    final String ignoredPattern = FileTypeManager.getInstance().getIgnoredFilesList();
    if (!ignoredPattern.contains(ORIG_FILE_PATTERN)) {
      final String newPattern = ignoredPattern + (ignoredPattern.endsWith(";") ? "" : ";") + ORIG_FILE_PATTERN;
      HgUtil.runWriteActionLater(new Runnable() {
        public void run() {
          FileTypeManager.getInstance().setIgnoredFilesList(newPattern);
        }
      });
    }
  }

  @Override
  public void deactivate() {
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

    myDirStateChangeListener.dispose();
  }

  @Nullable
  public static HgVcs getInstance(Project project) {
    if (project == null || project.isDisposed()) { return null; }
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (vcsManager == null) { return null; }
    return (HgVcs) vcsManager.findVcsByName(VCS_NAME);
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
      return (new File(ourTestHgExecutablePath, HG_EXECUTABLE_FILE_NAME)).getPath();
    }
    return globalSettings.getHgExecutable();
  }

  public HgGlobalSettings getGlobalSettings() {
    return globalSettings;
  }

  public void showMessageInConsole(String message, final TextAttributes style) {
    myVcsManager.addMessageToConsoleWindow(message, style);
  }

  public HgExecutableValidator getExecutableValidator() {
    synchronized (myExecutableValidatorLock) {
      if (myExecutableValidator == null) {
        myExecutableValidator = new HgExecutableValidator(myProject);
      }
      return myExecutableValidator;
    }
  }

  @Override
  public boolean reportsIgnoredDirectories() {
    return false;
  }

  @Override
  public List<CommitExecutor> getCommitExecutors() {
    return Collections.<CommitExecutor>singletonList(myCommitAndPushExecutor);
  }

}
