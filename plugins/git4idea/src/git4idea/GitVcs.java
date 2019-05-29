// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.roots.VcsRootDetector;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.VcsSynchronousProgressWrapper;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcs.log.VcsUserRegistry;
import git4idea.annotate.GitAnnotationProvider;
import git4idea.annotate.GitRepositoryForAnnotationsListener;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.changes.GitCommittedChangeListProvider;
import git4idea.changes.GitOutgoingChangesProvider;
import git4idea.checkin.GitCheckinEnvironment;
import git4idea.checkin.GitCommitAndPushExecutor;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import git4idea.config.GitExecutableManager;
import git4idea.config.GitExecutableValidator;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersion;
import git4idea.diff.GitDiffProvider;
import git4idea.history.GitHistoryProvider;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeProvider;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.rollback.GitRollbackEnvironment;
import git4idea.roots.GitIntegrationEnabler;
import git4idea.status.GitChangeProvider;
import git4idea.ui.branch.GitBranchWidget;
import git4idea.update.GitUpdateEnvironment;
import git4idea.util.GitVcsConsoleWriter;
import git4idea.vfs.GitVFSListener;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Git VCS implementation
 */
public class GitVcs extends AbstractVcs<CommittedChangeList> {

  public static final String NAME = "Git";
  public static final String ID = "git";

  private static final Logger LOG = Logger.getInstance(GitVcs.class.getName());
  private static final VcsKey ourKey = createKey(NAME);

  @Nullable private final ChangeProvider myChangeProvider;
  @Nullable private final GitCheckinEnvironment myCheckinEnvironment;
  private final RollbackEnvironment myRollbackEnvironment;
  @NotNull private final GitExecutableManager myExecutableManager;
  private final GitUpdateEnvironment myUpdateEnvironment;
  private final GitAnnotationProvider myAnnotationProvider;
  private final DiffProvider myDiffProvider;
  private final GitHistoryProvider myHistoryProvider;
  @NotNull private final Git myGit;
  private final GitVcsConsoleWriter myVcsConsoleWriter;
  private final RevisionSelector myRevSelector;
  private final GitCommittedChangeListProvider myCommittedChangeListProvider;

  private GitVFSListener myVFSListener; // a VFS listener that tracks file addition, deletion, and renaming.

  private final ReadWriteLock myCommandLock = new ReentrantReadWriteLock(true); // The command read/write lock
  @Nullable private final GitCommitAndPushExecutor myCommitAndPushExecutor;
  private final GitExecutableValidator myExecutableValidator;
  private GitBranchWidget myBranchWidget;

  private GitRepositoryForAnnotationsListener myRepositoryForAnnotationsListener;

  @NotNull
  public static GitVcs getInstance(@NotNull Project project) {
    return ObjectUtils.notNull((GitVcs)ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME));
  }

  public GitVcs(@NotNull Project project, @NotNull Git git,
                @NotNull GitVcsConsoleWriter vcsConsoleWriter,
                @NotNull final GitAnnotationProvider gitAnnotationProvider,
                @NotNull final GitDiffProvider gitDiffProvider,
                @NotNull final GitHistoryProvider gitHistoryProvider,
                @NotNull final GitRollbackEnvironment gitRollbackEnvironment,
                @NotNull final GitVcsSettings gitProjectSettings,
                @NotNull GitExecutableManager executableManager) {
    super(project, NAME);
    myGit = git;
    myVcsConsoleWriter = vcsConsoleWriter;
    myChangeProvider = project.isDefault() ? null : ServiceManager.getService(project, GitChangeProvider.class);
    myCheckinEnvironment = project.isDefault() ? null : ServiceManager.getService(project, GitCheckinEnvironment.class);
    myAnnotationProvider = gitAnnotationProvider;
    myDiffProvider = gitDiffProvider;
    myHistoryProvider = gitHistoryProvider;
    myRollbackEnvironment = gitRollbackEnvironment;
    myExecutableManager = executableManager;
    myRevSelector = new GitRevisionSelector();
    myUpdateEnvironment = new GitUpdateEnvironment(myProject, gitProjectSettings);
    myCommittedChangeListProvider = new GitCommittedChangeListProvider(myProject);
    myOutgoingChangesProvider = new GitOutgoingChangesProvider(myProject);
    myCommitAndPushExecutor = myCheckinEnvironment != null ? new GitCommitAndPushExecutor() : null;
    myExecutableValidator = new GitExecutableValidator(myProject);
  }


  public ReadWriteLock getCommandLock() {
    return myCommandLock;
  }

  /**
   * Run task in background using the common queue (per project)
   *
   * @param task the task to run
   */
  public static void runInBackground(Task.Backgroundable task) {
    task.queue();
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
  @Nullable
  public CheckinEnvironment createCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  @NotNull
  @Override
  public MergeProvider getMergeProvider() {
    return GitMergeProvider.detect(myProject);
  }

  @Override
  @NotNull
  public RollbackEnvironment createRollbackEnvironment() {
    return myRollbackEnvironment;
  }

  @Override
  @NotNull
  public GitHistoryProvider getVcsHistoryProvider() {
    return myHistoryProvider;
  }

  @Override
  public GitHistoryProvider getVcsBlockHistoryProvider() {
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
  public AnnotationProviderEx getAnnotationProvider() {
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
        VirtualFile root = GitUtil.getRepositoryForFile(myProject, path).getRoot();
        return GitRevisionNumber.resolve(myProject, root, revision);
      }
      catch (VcsException e) {
        LOG.info("Unexpected problem with resolving the git revision number: ", e);
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
  protected void activate() {
    ApplicationManager.getApplication().executeOnPooledThread(
      () -> ProgressManager.getInstance().executeProcessUnderProgress(
        () -> myExecutableManager.testGitExecutableVersionValid(myProject), new EmptyProgressIndicator()));

    if (myVFSListener == null) {
      myVFSListener = GitVFSListener.createInstance(this, myGit, myVcsConsoleWriter);
    }
    ServiceManager.getService(myProject, VcsUserRegistry.class); // make sure to read the registry before opening commit dialog

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myBranchWidget = new GitBranchWidget(myProject);
      myBranchWidget.activate();
    }
    if (myRepositoryForAnnotationsListener == null) {
      myRepositoryForAnnotationsListener = new GitRepositoryForAnnotationsListener(myProject);
    }
    GitUserRegistry.getInstance(myProject).activate();
    GitBranchIncomingOutgoingManager.getInstance(myProject).activate();
  }

  @Override
  protected void deactivate() {
    if (myVFSListener != null) {
      Disposer.dispose(myVFSListener);
      myVFSListener = null;
    }

    if (myBranchWidget != null) {
      myBranchWidget.deactivate();
      myBranchWidget = null;
    }
  }


  @Override
  public Configurable getConfigurable() {
    return null;
  }

  @Override
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
  public void showErrors(@NotNull List<? extends VcsException> list, @NotNull String action) {
    if (list.size() > 0) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("\n");
      buffer.append(GitBundle.message("error.list.title", action));
      for (final VcsException exception : list) {
        buffer.append("\n");
        buffer.append(exception.getMessage());
      }
      final String msg = buffer.toString();
      UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(myProject, msg, GitBundle.getString("error.dialog.title")));
    }
  }

  /**
   * @return the version number of Git, which is used by IDEA. Or {@link GitVersion#NULL} if version info is unavailable yet.
   * @see GitExecutableManager#getVersionOrCancel
   */
  @NotNull
  public GitVersion getVersion() {
    return myExecutableManager.getVersion(myProject);
  }

  /**
   * Shows a command line message in the Version Control Console
   * @deprecated use {@link GitVcsConsoleWriter}
   */
  @Deprecated
  public void showCommandLine(final String cmdLine) {
    myVcsConsoleWriter.showCommandLine(cmdLine);
  }

  @Override
  public boolean allowsNestedRoots() {
    return true;
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public VcsType getType() {
    return VcsType.distributed;
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
  public List<CommitExecutor> getCommitExecutors() {
    return myCommitAndPushExecutor != null
           ? Collections.singletonList(myCommitAndPushExecutor)
           : Collections.emptyList();
  }

  @Deprecated
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
    Runnable task = () -> {
      Collection<VcsRoot> roots = ServiceManager.getService(myProject, VcsRootDetector.class).detect();
      new GitIntegrationEnabler(this, myGit).enable(roots);
    };
    BackgroundTaskUtil.executeOnPooledThread(myProject, task);
  }

  @Override
  public CheckoutProvider getCheckoutProvider() {
    return new GitCheckoutProvider();
  }

  @Nullable
  @Override
  public CommittedChangeList loadRevisions(VirtualFile vf, VcsRevisionNumber number) {
    GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(vf);
    if (repository == null) return null;

    return VcsSynchronousProgressWrapper.compute(
      () -> GitCommittedChangeListProvider.getCommittedChangeList(myProject, repository.getRoot(), (GitRevisionNumber)number),
      getProject(), "Load Revision Contents");
  }

  @Override
  public boolean arePartialChangelistsSupported() {
    return true;
  }

  @TestOnly
  public GitVFSListener getVFSListener() {
    return myVFSListener;
  }
}
