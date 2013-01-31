/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.containers.ComparatorDelegate;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import git4idea.annotate.GitAnnotationProvider;
import git4idea.annotate.GitRepositoryForAnnotationsListener;
import git4idea.changes.GitCommittedChangeListProvider;
import git4idea.changes.GitOutgoingChangesProvider;
import git4idea.checkin.GitCheckinEnvironment;
import git4idea.checkin.GitCommitAndPushExecutor;
import git4idea.commands.Git;
import git4idea.config.*;
import git4idea.diff.GitDiffProvider;
import git4idea.diff.GitTreeDiffProvider;
import git4idea.history.GitHistoryProvider;
import git4idea.history.NewGitUsersComponent;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.GitProjectLogManager;
import git4idea.history.wholeTree.GitCommitDetailsProvider;
import git4idea.history.wholeTree.GitCommitsSequentialIndex;
import git4idea.history.wholeTree.GitCommitsSequentially;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeProvider;
import git4idea.rollback.GitRollbackEnvironment;
import git4idea.roots.GitIntegrationEnabler;
import git4idea.roots.GitRootChecker;
import git4idea.roots.GitRootDetectInfo;
import git4idea.roots.GitRootDetector;
import git4idea.status.GitChangeProvider;
import git4idea.ui.branch.GitBranchWidget;
import git4idea.update.GitUpdateEnvironment;
import git4idea.vfs.GitVFSListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Git VCS implementation
 */
public class GitVcs extends AbstractVcs<CommittedChangeList> {
  public static final NotificationGroup NOTIFICATION_GROUP_ID = NotificationGroup.toolWindowGroup(
    "Git Messages", ChangesViewContentManager.TOOLWINDOW_ID, true);
  public static final NotificationGroup IMPORTANT_ERROR_NOTIFICATION = new NotificationGroup(
    "Git Important Messages", NotificationDisplayType.STICKY_BALLOON, true);
  public static final NotificationGroup MINOR_NOTIFICATION = new NotificationGroup(
    "Git Minor Notifications", NotificationDisplayType.BALLOON, true);

  static {
    NotificationsConfigurationImpl.remove("Git");
  }

  public static final String NAME = "Git";

  /**
   * Provide selected Git commit in some commit list. Use this, when {@link Change} is not enough.
   * @see VcsDataKeys#CHANGES
   * @see #SELECTED_COMMITS
   */
  public static final DataKey<GitCommit> GIT_COMMIT = DataKey.create("Git.Commit");

  /**
   * Provides the list of Git commits selected in some list, for example, in the Git log.
   * @see #GIT_COMMIT
   */
  public static final DataKey<List<GitCommit>> SELECTED_COMMITS = DataKey.create("Git.Selected.Commits");

  /**
   * Provides the possibility to receive on demand those commit details which usually are not accessible from the {@link GitCommit} object.
   */
  public static final DataKey<GitCommitDetailsProvider> COMMIT_DETAILS_PROVIDER = DataKey.create("Git.Commits.Details.Provider");

  private static final Logger log = Logger.getInstance(GitVcs.class.getName());
  private static final VcsKey ourKey = createKey(NAME);

  private final ChangeProvider myChangeProvider;
  private final GitCheckinEnvironment myCheckinEnvironment;
  private final RollbackEnvironment myRollbackEnvironment;
  private final GitUpdateEnvironment myUpdateEnvironment;
  private final GitAnnotationProvider myAnnotationProvider;
  private final DiffProvider myDiffProvider;
  private final VcsHistoryProvider myHistoryProvider;
  @NotNull private final Git myGit;
  private final ProjectLevelVcsManager myVcsManager;
  private final GitVcsApplicationSettings myAppSettings;
  private final Configurable myConfigurable;
  private final RevisionSelector myRevSelector;
  private final GitMergeProvider myMergeProvider;
  private final GitMergeProvider myReverseMergeProvider;
  private final GitCommittedChangeListProvider myCommittedChangeListProvider;
  private final @NotNull GitPlatformFacade myPlatformFacade;

  private GitVFSListener myVFSListener; // a VFS listener that tracks file addition, deletion, and renaming.

  private final ReadWriteLock myCommandLock = new ReentrantReadWriteLock(true); // The command read/write lock
  private final TreeDiffProvider myTreeDiffProvider;
  private final GitCommitAndPushExecutor myCommitAndPushExecutor;
  private final GitExecutableValidator myExecutableValidator;
  private GitBranchWidget myBranchWidget;

  private GitVersion myVersion = GitVersion.NULL; // version of Git which this plugin uses.
  private static final int MAX_CONSOLE_OUTPUT_SIZE = 10000;
  private GitRepositoryForAnnotationsListener myRepositoryForAnnotationsListener;

  @Nullable
  public static GitVcs getInstance(Project project) {
    if (project == null || project.isDisposed()) {
      return null;
    }
    return (GitVcs) ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME);
  }

  public GitVcs(@NotNull Project project, @NotNull Git git,
                @NotNull final ProjectLevelVcsManager gitVcsManager,
                @NotNull final GitAnnotationProvider gitAnnotationProvider,
                @NotNull final GitDiffProvider gitDiffProvider,
                @NotNull final GitHistoryProvider gitHistoryProvider,
                @NotNull final GitRollbackEnvironment gitRollbackEnvironment,
                @NotNull final GitVcsApplicationSettings gitSettings,
                @NotNull final GitVcsSettings gitProjectSettings) {
    super(project, NAME);
    myGit = git;
    myVcsManager = gitVcsManager;
    myAppSettings = gitSettings;
    myChangeProvider = project.isDefault() ? null : ServiceManager.getService(project, GitChangeProvider.class);
    myCheckinEnvironment = project.isDefault() ? null : ServiceManager.getService(project, GitCheckinEnvironment.class);
    myAnnotationProvider = gitAnnotationProvider;
    myDiffProvider = gitDiffProvider;
    myHistoryProvider = gitHistoryProvider;
    myRollbackEnvironment = gitRollbackEnvironment;
    myRevSelector = new GitRevisionSelector();
    myConfigurable = new GitVcsConfigurable(gitProjectSettings, myProject);
    myUpdateEnvironment = new GitUpdateEnvironment(myProject, this, gitProjectSettings);
    myMergeProvider = new GitMergeProvider(myProject);
    myReverseMergeProvider = new GitMergeProvider(myProject, true);
    myCommittedChangeListProvider = new GitCommittedChangeListProvider(myProject);
    myOutgoingChangesProvider = new GitOutgoingChangesProvider(myProject);
    myTreeDiffProvider = new GitTreeDiffProvider(myProject);
    myCommitAndPushExecutor = new GitCommitAndPushExecutor(myCheckinEnvironment);
    myExecutableValidator = new GitExecutableValidator(myProject, this);
    myPlatformFacade = ServiceManager.getService(myProject, GitPlatformFacade.class);
  }


  public ReadWriteLock getCommandLock() {
    return myCommandLock;
  }

  /**
   * Run task in background using the common queue (per project)
   * @param task the task to run
   */
  public static void runInBackground(Task.Backgroundable task) {
    task.queue();
  }

  /**
   * @return a reverse merge provider for git (with reversed meaning of "theirs" and "yours", needed for the rebase and unstash)
   */
  @NotNull
  public MergeProvider getReverseMergeProvider() {
    return myReverseMergeProvider;
  }

  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return myCommittedChangeListProvider;
  }

  @Override
  public String getRevisionPattern() {
    // return the full commit hash pattern, possibly other revision formats should be supported as well
    return "[0-9a-fA-F]+";
  }

  @Override
  @NotNull
  public CheckinEnvironment createCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  @NotNull
  @Override
  public MergeProvider getMergeProvider() {
    return myMergeProvider;
  }

  @Override
  @NotNull
  public RollbackEnvironment createRollbackEnvironment() {
    return myRollbackEnvironment;
  }

  @Override
  @NotNull
  public VcsHistoryProvider getVcsHistoryProvider() {
    return myHistoryProvider;
  }

  @Override
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return myHistoryProvider;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return NAME;
  }

  @Override
  @Nullable
  public UpdateEnvironment createUpdateEnvironment() {
    return myUpdateEnvironment;
  }

  @Override
  @NotNull
  public GitAnnotationProvider getAnnotationProvider() {
    return myAnnotationProvider;
  }

  @Override
  @NotNull
  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  @Override
  @Nullable
  public RevisionSelector getRevisionSelector() {
    return myRevSelector;
  }

  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(@Nullable String revision, @Nullable FilePath path) throws VcsException {
    if (revision == null || revision.length() == 0) return null;
    if (revision.length() > 40) {    // date & revision-id encoded string
      String dateString = revision.substring(0, revision.indexOf("["));
      String rev = revision.substring(revision.indexOf("[") + 1, 40);
      Date d = new Date(Date.parse(dateString));
      return new GitRevisionNumber(rev, d);
    }
    if (path != null) {
      try {
        VirtualFile root = GitUtil.getGitRoot(path);
        return GitRevisionNumber.resolve(myProject, root, revision);
      }
      catch (VcsException e) {
        log.info("Unexpected problem with resolving the git revision number: ", e);
        throw e;
      }
    }
    return new GitRevisionNumber(revision);

  }

  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(@Nullable String revision) throws VcsException {
    return parseRevisionNumber(revision, null);
  }

  @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    return dir.isDirectory() && GitUtil.gitRootOrNull(dir) != null;
  }

  @Override
  public VcsRootChecker getRootChecker() {
    return new GitRootChecker(myProject, myPlatformFacade);
  }

  @Override
  protected void start() throws VcsException {
  }

  @Override
  protected void shutdown() throws VcsException {
  }

  @Override
  protected void activate() {
    checkExecutableAndVersion();

    if (myVFSListener == null) {
      myVFSListener = new GitVFSListener(myProject, this, myGit);
    }
    NewGitUsersComponent.getInstance(myProject).activate();
    GitProjectLogManager.getInstance(myProject).activate();

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
      if (statusBar != null) {
        myBranchWidget = new GitBranchWidget(myProject);
        statusBar.addWidget(myBranchWidget, "after " + (SystemInfo.isMac ? "Encoding" : "InsertOverwrite"), myProject);
      }
    }
    if (myRepositoryForAnnotationsListener == null) {
      myRepositoryForAnnotationsListener = new GitRepositoryForAnnotationsListener(myProject);
    }
    ((GitCommitsSequentialIndex) ServiceManager.getService(GitCommitsSequentially.class)).activate();
  }

  private void checkExecutableAndVersion() {
    boolean executableIsAlreadyCheckedAndFine = false;
    String pathToGit = myAppSettings.getPathToGit();
    if (!pathToGit.contains(File.separator)) { // no path, just sole executable, with a hope that it is in path
      // subject to redetect the path if executable validator fails
      if (!myExecutableValidator.isExecutableValid()) {
        myAppSettings.setPathToGit(new GitExecutableDetector().detect());
      }
      else {
        executableIsAlreadyCheckedAndFine = true; // not to check it twice
      }
    }

    if (executableIsAlreadyCheckedAndFine || myExecutableValidator.checkExecutableAndNotifyIfNeeded()) {
      checkVersion();
    }
  }

  @Override
  protected void deactivate() {
    if (myVFSListener != null) {
      Disposer.dispose(myVFSListener);
      myVFSListener = null;
    }
    NewGitUsersComponent.getInstance(myProject).deactivate();
    GitProjectLogManager.getInstance(myProject).deactivate();

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar != null && myBranchWidget != null) {
      statusBar.removeWidget(myBranchWidget.ID());
      myBranchWidget = null;
    }
    ((GitCommitsSequentialIndex) ServiceManager.getService(GitCommitsSequentially.class)).deactivate();
  }

  @NotNull
  @Override
  public synchronized Configurable getConfigurable() {
    return myConfigurable;
  }

  @Nullable
  public ChangeProvider getChangeProvider() {
    return myChangeProvider;
  }

  /**
   * Show errors as popup and as messages in vcs view.
   *
   * @param list   a list of errors
   * @param action an action
   */
  public void showErrors(@NotNull List<VcsException> list, @NotNull String action) {
    if (list.size() > 0) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("\n");
      buffer.append(GitBundle.message("error.list.title", action));
      for (final VcsException exception : list) {
        buffer.append("\n");
        buffer.append(exception.getMessage());
      }
      final String msg = buffer.toString();
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(myProject, msg, GitBundle.getString("error.dialog.title"));
        }
      });
    }
  }

  /**
   * Shows a plain message in the Version Control Console.
   */
  public void showMessages(@NotNull String message) {
    if (message.length() == 0) return;
    showMessage(message, ConsoleViewContentType.NORMAL_OUTPUT.getAttributes());
  }

  /**
   * Show message in the Version Control Console
   * @param message a message to show
   * @param style   a style to use
   */
  private void showMessage(@NotNull String message, final TextAttributes style) {
    if (message.length() > MAX_CONSOLE_OUTPUT_SIZE) {
      message = message.substring(0, MAX_CONSOLE_OUTPUT_SIZE);
    }
    myVcsManager.addMessageToConsoleWindow(message, style);
  }

  /**
   * Checks Git version and updates the myVersion variable.
   * In the case of exception or unsupported version reports the problem.
   * Note that unsupported version is also applied - some functionality might not work (we warn about that), but no need to disable at all.
   */
  public void checkVersion() {
    final String executable = myAppSettings.getPathToGit();
    try {
      myVersion = GitVersion.identifyVersion(executable);
      if (! myVersion.isSupported()) {
        String message = GitBundle.message("vcs.unsupported.version", myVersion, GitVersion.MIN);
        if (! myProject.isDefault()) {
          showMessage(message, ConsoleViewContentType.SYSTEM_OUTPUT.getAttributes());
        }
        VcsBalloonProblemNotifier.showOverVersionControlView(myProject, message, MessageType.ERROR);
      }
    } catch (Exception e) {
      if (getExecutableValidator().checkExecutableAndNotifyIfNeeded()) { // check executable before notifying error
        final String reason = (e.getCause() != null ? e.getCause() : e).getMessage();
        String message = GitBundle.message("vcs.unable.to.run.git", executable, reason);
        if (!myProject.isDefault()) {
          showMessage(message, ConsoleViewContentType.SYSTEM_OUTPUT.getAttributes());
        }
        VcsBalloonProblemNotifier.showOverVersionControlView(myProject, message, MessageType.ERROR);
      }
    }
  }

  /**
   * @return the version number of Git, which is used by IDEA. Or {@link GitVersion#NULL} if version info is unavailable yet.
   */
  @NotNull
  public GitVersion getVersion() {
    return myVersion;
  }

  /**
   * Shows a command line message in the Version Control Console
   */
  public void showCommandLine(final String cmdLine) {
    SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss.SSS");
    showMessage(f.format(new Date()) + ": " + cmdLine, ConsoleViewContentType.SYSTEM_OUTPUT.getAttributes());
  }

  /**
   * Shows error message in the Version Control Console
   */
  public void showErrorMessages(final String line) {
    showMessage(line, ConsoleViewContentType.ERROR_OUTPUT.getAttributes());
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
      final VirtualFile childRoot = GitUtil.gitRootOrNull(child);
      if (childRoot == null) {
        // non-git file actually, skip it
        continue;
      }
      for (int j = i - 1; j >= 0; --j) {
        final S sParent = in.get(j);
        final VirtualFile parent = convertor.convert(sParent);
        // the method check both that parent is an ancestor of the child and that they share common git root
        if (VfsUtilCore.isAncestor(parent, child, false) && VfsUtilCore.isAncestor(childRoot, parent, false)) {
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
    return GitRootConverter.INSTANCE;
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public VcsType getType() {
    return VcsType.distibuted;
  }

  private final VcsOutgoingChangesProvider<CommittedChangeList> myOutgoingChangesProvider;

  @Override
  protected VcsOutgoingChangesProvider<CommittedChangeList> getOutgoingProviderImpl() {
    return myOutgoingChangesProvider;
  }

  @Override
  public RemoteDifferenceStrategy getRemoteDifferenceStrategy() {
    return RemoteDifferenceStrategy.ASK_TREE_PROVIDER;
  }

  @Override
  protected TreeDiffProvider getTreeDiffProviderImpl() {
    return myTreeDiffProvider;
  }

  @Override
  public List<CommitExecutor> getCommitExecutors() {
    return Collections.<CommitExecutor>singletonList(myCommitAndPushExecutor);
  }

  @NotNull
  public GitExecutableValidator getExecutableValidator() {
    return myExecutableValidator;
  }

  @Override
  public boolean fileListenerIsSynchronous() {
    return false;
  }

  @Override
  @CalledInAwt
  public void enableIntegration() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        GitRootDetectInfo detectInfo = new GitRootDetector(myProject, myPlatformFacade).detect();
        new GitIntegrationEnabler(myProject, myGit, myPlatformFacade).enable(detectInfo);
      }
    });
  }
}
