// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.VcsSynchronousProgressWrapper;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcs.log.VcsUserRegistry;
import git4idea.annotate.GitAdvancedSettingsListener;
import git4idea.annotate.GitAnnotationProvider;
import git4idea.annotate.GitAnnotationsListener;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.changes.GitCommittedChangeListProvider;
import git4idea.changes.GitOutgoingChangesProvider;
import git4idea.checkin.GitCheckinEnvironment;
import git4idea.checkin.GitCommitAndPushExecutor;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.config.*;
import git4idea.diff.GitDiffProvider;
import git4idea.history.GitHistoryProvider;
import git4idea.i18n.GitBundle;
import git4idea.index.GitStageManagerKt;
import git4idea.merge.GitMergeProvider;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.rollback.GitRollbackEnvironment;
import git4idea.roots.GitIntegrationEnabler;
import git4idea.stash.ui.GitStashContentProviderKt;
import git4idea.status.GitChangeProvider;
import git4idea.update.GitUpdateEnvironment;
import git4idea.vfs.GitVFSListener;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Git VCS implementation
 */
public final class GitVcs extends AbstractVcs {
  public static final Supplier<@Nls String> DISPLAY_NAME = GitBundle.messagePointer("git4idea.vcs.name");
  public static final @NonNls String NAME = "Git";
  public static final @NonNls String ID = "git";

  private static final Logger LOG = Logger.getInstance(GitVcs.class.getName());
  private static final VcsKey ourKey = createKey(NAME);

  private Disposable myDisposable;
  private GitVFSListener myVFSListener; // a VFS listener that tracks file addition, deletion, and renaming.

  private final ReadWriteLock myCommandLock = new ReentrantReadWriteLock(true); // The command read/write lock

  @NotNull
  public static GitVcs getInstance(@NotNull Project project) {
    GitVcs gitVcs = (GitVcs)ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME);
    ProgressManager.checkCanceled();
    return Objects.requireNonNull(gitVcs);
  }

  public GitVcs(@NotNull Project project) {
    super(project, NAME);
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
    return myProject.getService(GitCommittedChangeListProvider.class);
  }

  @Override
  public String getRevisionPattern() {
    // return the full commit hash pattern, possibly other revision formats should be supported as well
    return "[0-9a-fA-F]+";
  }

  @Override
  @Nullable
  public CheckinEnvironment getCheckinEnvironment() {
    if (myProject.isDefault()) return null;
    return myProject.getService(GitCheckinEnvironment.class);
  }

  @NotNull
  @Override
  public MergeProvider getMergeProvider() {
    return GitMergeProvider.detect(myProject);
  }

  @Override
  @NotNull
  public RollbackEnvironment getRollbackEnvironment() {
    return myProject.getService(GitRollbackEnvironment.class);
  }

  @Override
  @NotNull
  public GitHistoryProvider getVcsHistoryProvider() {
    return myProject.getService(GitHistoryProvider.class);
  }

  @Override
  public GitHistoryProvider getVcsBlockHistoryProvider() {
    return myProject.getService(GitHistoryProvider.class);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME.get();
  }

  @Nls
  @NotNull
  @Override
  public String getShortNameWithMnemonic() {
    return GitBundle.message("git4idea.vcs.name.with.mnemonic");
  }

  @Override
  @Nullable
  public UpdateEnvironment getUpdateEnvironment() {
    return myProject.getService(GitUpdateEnvironment.class);
  }

  @Override
  @NotNull
  public AnnotationProviderEx getAnnotationProvider() {
    return myProject.getService(GitAnnotationProvider.class);
  }

  @Override
  @NotNull
  public DiffProvider getDiffProvider() {
    return myProject.getService(GitDiffProvider.class);
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
        VirtualFile root = GitUtil.getRootForFile(myProject, path);
        return GitRevisionNumber.resolve(myProject, root, revision);
      }
      catch (VcsException e) {
        LOG.warn("Unexpected problem with resolving the git revision number: ", e);
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
    return dir.isDirectory() && GitUtil.isUnderGit(dir);
  }

  @Override
  protected void activate() {
    Disposable disposable = Disposer.newDisposable();
    myDisposable = disposable;

    BackgroundTaskUtil.executeOnPooledThread(disposable, ()
      -> GitExecutableManager.getInstance().testGitExecutableVersionValid(myProject));

    myVFSListener = GitVFSListener.createInstance(this, disposable);
    // make sure to read the registry before opening commit dialog
    myProject.getService(VcsUserRegistry.class);

    GitAnnotationsListener.registerListener(myProject, disposable);
    GitAdvancedSettingsListener.registerListener(myProject, disposable);

    GitUserRegistry.getInstance(myProject).activate();
    GitBranchIncomingOutgoingManager.getInstance(myProject).activate();
  }

  @Override
  protected void deactivate() {
    myVFSListener = null;
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
  }

  @Override
  @Nullable
  public GitChangeProvider getChangeProvider() {
    if (myProject.isDefault()) return null;
    return myProject.getService(GitChangeProvider.class);
  }

  /**
   * Show errors as popup and as messages in vcs view.
   *
   * @param list   a list of errors
   * @param action an action
   */
  public void showErrors(@NotNull List<? extends VcsException> list, @NotNull @Nls String action) {
    if (list.size() > 0) {
      @Nls StringBuilder buffer = new StringBuilder();
      buffer.append("\n");
      buffer.append(GitBundle.message("error.list.title", action));
      for (final VcsException exception : list) {
        buffer.append("\n");
        buffer.append(exception.getMessage());
      }
      final String msg = buffer.toString();
      UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(myProject, msg, GitBundle.message("error.dialog.title")));
    }
  }

  /**
   * @return the version number of Git, which is used by IDEA. Or {@link GitVersion#NULL} if version info is unavailable yet.
   */
  @NotNull
  public GitVersion getVersion() {
    return GitExecutableManager.getInstance().getVersion(myProject);
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

  @Override
  protected VcsOutgoingChangesProvider<CommittedChangeList> getOutgoingProviderImpl() {
    return myProject.getService(GitOutgoingChangesProvider.class);
  }

  @Override
  public RemoteDifferenceStrategy getRemoteDifferenceStrategy() {
    return RemoteDifferenceStrategy.ASK_TREE_PROVIDER;
  }

  @Override
  public List<CommitExecutor> getCommitExecutors() {
    if (myProject.isDefault()) return Collections.emptyList();
    return Collections.singletonList(myProject.getService(GitCommitAndPushExecutor.class));
  }


  /**
   * @deprecated Use {@link GitExecutableManager#identifyVersion(String)} and {@link GitExecutableProblemsNotifier}.
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public GitExecutableValidator getExecutableValidator() {
    return new GitExecutableValidator(myProject);
  }

  @Override
  public boolean fileListenerIsSynchronous() {
    return false;
  }

  @Override
  @RequiresEdt
  public void enableIntegration(@Nullable VirtualFile targetDirectory) {
    new Task.Backgroundable(myProject, GitBundle.message("progress.title.enabling.git"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        new GitIntegrationEnabler(GitVcs.this, targetDirectory).detectAndEnable();
      }
    }.queue();
  }

  @Override
  public CheckoutProvider getCheckoutProvider() {
    return new GitCheckoutProvider();
  }

  @Nullable
  @Override
  public CommittedChangeList loadRevisions(@NotNull VirtualFile vf, @NotNull VcsRevisionNumber number) {
    GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(vf);
    if (repository == null) return null;

    return VcsSynchronousProgressWrapper.compute(
      () -> GitCommittedChangeListProvider.getCommittedChangeList(myProject, repository.getRoot(), (GitRevisionNumber)number),
      getProject(), GitBundle.message("revision.load.contents"));
  }

  @Override
  public boolean arePartialChangelistsSupported() {
    return true;
  }

  @TestOnly
  public GitVFSListener getVFSListener() {
    return myVFSListener;
  }

  @Override
  public boolean needsCaseSensitiveDirtyScope() {
    return true;
  }

  @Override
  public boolean isWithCustomMenu() {
    return true;
  }

  @Override
  public boolean isWithCustomLocalChanges() {
    return GitVcsApplicationSettings.getInstance().isStagingAreaEnabled() &&
           GitStageManagerKt.canEnableStagingArea();
  }

  @Override
  public boolean isWithCustomShelves() {
    return GitStashContentProviderKt.stashToolWindowRegistryOption().asBoolean();
  }

  @Override
  public @Nullable String getCompareWithTheSameVersionActionName() {
    return ActionsBundle.message("action.Diff.ShowDiff.text");
  }
}
