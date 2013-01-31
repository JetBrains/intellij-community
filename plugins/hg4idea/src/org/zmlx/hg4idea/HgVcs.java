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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
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
import org.zmlx.hg4idea.status.HgRemoteStatusUpdater;
import org.zmlx.hg4idea.status.ui.HgCurrentBranchStatusUpdater;
import org.zmlx.hg4idea.status.ui.HgHideableWidget;
import org.zmlx.hg4idea.status.ui.HgIncomingOutgoingWidget;
import org.zmlx.hg4idea.status.ui.HgStatusWidget;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class HgVcs extends AbstractVcs<CommittedChangeList> {

  public static final Topic<HgUpdater> BRANCH_TOPIC = new Topic<HgUpdater>("hg4idea.branch", HgUpdater.class);
  public static final Topic<HgUpdater> REMOTE_TOPIC = new Topic<HgUpdater>("hg4idea.remote", HgUpdater.class);
  public static final Topic<HgUpdater> STATUS_TOPIC = new Topic<HgUpdater>("hg4idea.status", HgUpdater.class);
  public static final Topic<HgHideableWidget> INCOMING_OUTGOING_CHECK_TOPIC =
    new Topic<HgHideableWidget>("hg4idea.incomingcheck", HgHideableWidget.class);
  private static final Logger LOG = Logger.getInstance(HgVcs.class);

  public static final String VCS_NAME = "hg4idea";
  public static final String HG_EXECUTABLE_FILE_NAME = (SystemInfo.isWindows ? "hg.exe" : "hg");
  private final static VcsKey ourKey = createKey(VCS_NAME);
  private static final int MAX_CONSOLE_OUTPUT_SIZE = 10000;

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
  protected final @NotNull HgPlatformFacade myPlatformFacade;
  private MessageBusConnection messageBusConnection;
  @NotNull private final HgGlobalSettings globalSettings;
  @NotNull private final HgProjectSettings projectSettings;
  private final ProjectLevelVcsManager myVcsManager;

  private HgVFSListener myVFSListener;
  private final HgMergeProvider myMergeProvider;
  private HgExecutableValidator myExecutableValidator;
  private final Object myExecutableValidatorLock = new Object();
  private File myPromptHooksExtensionFile;
  private CommitExecutor myCommitAndPushExecutor;

  private HgRemoteStatusUpdater myHgRemoteStatusUpdater;
  private HgCurrentBranchStatusUpdater myHgCurrentBranchStatusUpdater;
  private HgStatusWidget myStatusWidget;
  private HgIncomingOutgoingWidget myIncomingWidget;
  private HgIncomingOutgoingWidget myOutgoingWidget;

  public HgVcs(Project project,
               @NotNull HgGlobalSettings globalSettings,
               @NotNull HgProjectSettings projectSettings,
               ProjectLevelVcsManager vcsManager, HgPlatformFacade platformFacade) {
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
    commitedChangesProvider = new HgCachingCommitedChangesProvider(project, this);
    myMergeProvider = new HgMergeProvider(myProject);
    myCommitAndPushExecutor = new HgCommitAndPushExecutor(checkinEnvironment);
    myPlatformFacade = platformFacade;
  }

  public String getDisplayName() {
    return HgVcsMessages.message("hg4idea.mercurial");
  }

  public Configurable getConfigurable() {
    return new HgProjectConfigurable(getProject(), projectSettings);
  }

  @NotNull
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
    return commitedChangesProvider;
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
  @NotNull
  public File getPromptHooksExtensionFile() {
    if (myPromptHooksExtensionFile == null) {
      // check that hooks are available
      myPromptHooksExtensionFile = HgUtil.getTemporaryPythonFile("prompthooks");
      if (myPromptHooksExtensionFile == null || !myPromptHooksExtensionFile.exists()) {
        LOG.error(
          "prompthooks.py Mercurial extension is not found. Please reinstall " + ApplicationNamesInfo.getInstance().getProductName());
      }
    }
    return myPromptHooksExtensionFile;
  }

  @Override
  public void activate() {
    super.activate();

    // validate hg executable on start
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getExecutableValidator().checkExecutableAndNotifyIfNeeded();
    }

    // status bar
    myStatusWidget = new HgStatusWidget(this, getProject(), projectSettings);
    myStatusWidget.activate();

    myIncomingWidget = new HgIncomingOutgoingWidget(this, getProject(), projectSettings, true);
    myIncomingWidget.activate();

    myOutgoingWidget = new HgIncomingOutgoingWidget(this, getProject(), projectSettings, false);
    myOutgoingWidget.activate();

    // updaters and listeners
    myHgRemoteStatusUpdater =
      new HgRemoteStatusUpdater(this, myIncomingWidget.getChangesetStatus(), myOutgoingWidget.getChangesetStatus(),
                                projectSettings);
    myHgRemoteStatusUpdater.activate();

    myHgCurrentBranchStatusUpdater = new HgCurrentBranchStatusUpdater(this, myStatusWidget.getCurrentBranchStatus());
    myHgCurrentBranchStatusUpdater.activate();

    messageBusConnection = myProject.getMessageBus().connect();
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(FileEditorManagerEvent event) {
        Project project = event.getManager().getProject();
        project.getMessageBus().syncPublisher(BRANCH_TOPIC).update(project, null);
      }
    });

    myVFSListener = new HgVFSListener(myProject, this);

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

    // Force a branch topic update
    myProject.getMessageBus().syncPublisher(BRANCH_TOPIC).update(myProject, null);
  }

  @Override
  public void deactivate() {
    if (null != myHgRemoteStatusUpdater) {
      myHgRemoteStatusUpdater.deactivate();
      myHgRemoteStatusUpdater = null;
    }
    if (null != myHgCurrentBranchStatusUpdater) {
      myHgCurrentBranchStatusUpdater.deactivate();
      myHgCurrentBranchStatusUpdater = null;
    }
    if (null != myStatusWidget) {
      myStatusWidget.deactivate();
      myStatusWidget = null;
    }
    if (null != myIncomingWidget) {
      myIncomingWidget.deactivate();
      myIncomingWidget = null;
    }
    if (null != myOutgoingWidget) {
      myOutgoingWidget.deactivate();
      myOutgoingWidget = null;
    }
    if (messageBusConnection != null) {
      messageBusConnection.disconnect();
    }

    if (myVFSListener != null) {
      Disposer.dispose(myVFSListener);
      myVFSListener = null;
    }

    super.deactivate();
  }

  @Nullable
  public static HgVcs getInstance(Project project) {
    if (project == null || project.isDisposed()) {
      return null;
    }
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (vcsManager == null) {
      return null;
    }
    return (HgVcs)vcsManager.findVcsByName(VCS_NAME);
  }

  private static String ourTestHgExecutablePath; // path to hg in test mode

  /**
   * Sets the path to hg executable used in the test mode.
   */
  public static void setTestHgExecutablePath(String path) {
    ourTestHgExecutablePath = path;
  }

  @NotNull
  public HgGlobalSettings getGlobalSettings() {
    return globalSettings;
  }

  public void showMessageInConsole(String message, final TextAttributes style) {
    if (message.length() > MAX_CONSOLE_OUTPUT_SIZE) {
      message = message.substring(0, MAX_CONSOLE_OUTPUT_SIZE);
    }
    myVcsManager.addMessageToConsoleWindow(message, style);
  }

  public HgExecutableValidator getExecutableValidator() {
    synchronized (myExecutableValidatorLock) {
      if (myExecutableValidator == null) {
        myExecutableValidator = new HgExecutableValidator(myProject, this);
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
    return Collections.singletonList(myCommitAndPushExecutor);
  }

  public static VcsKey getKey() {
    return ourKey;
  }
}
